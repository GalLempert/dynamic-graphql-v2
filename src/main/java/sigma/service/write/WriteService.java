package sigma.service.write;

import sigma.dto.request.*;
import sigma.dto.response.*;
import sigma.filter.FilterTranslator;
import sigma.model.DynamicDocument;
import sigma.model.Endpoint;
import sigma.model.filter.FilterResult;
import sigma.persistence.repository.DynamicDocumentRepository;
import sigma.service.write.subentity.SubEntityProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Service for executing write operations with ACID transaction support
 */
@Service
@Transactional
public class WriteService {

    private static final Logger logger = LoggerFactory.getLogger(WriteService.class);

    private final DynamicDocumentRepository repository;
    private final FilterTranslator filterTranslator;
    private final SubEntityProcessor subEntityProcessor;
    private final DocumentChangeDetector documentChangeDetector;
    private final ThreadLocal<Endpoint> endpointContext = new ThreadLocal<>();

    public WriteService(DynamicDocumentRepository repository,
                        FilterTranslator filterTranslator,
                        SubEntityProcessor subEntityProcessor,
                        DocumentChangeDetector documentChangeDetector) {
        this.repository = repository;
        this.filterTranslator = filterTranslator;
        this.subEntityProcessor = subEntityProcessor;
        this.documentChangeDetector = documentChangeDetector;
    }

    private Endpoint requireEndpointContext() {
        Endpoint endpoint = endpointContext.get();
        if (endpoint == null) {
            throw new IllegalStateException("Endpoint context is not available for the current write operation");
        }
        return endpoint;
    }

    /**
     * Executes a write request using endpoint metadata for sub-entity handling
     */
    public WriteResponse execute(WriteRequest request, Endpoint endpoint) {
        logger.info("Executing {} operation on endpoint: {} -> table: {}",
                request.getType(), endpoint.getName(), endpoint.getDatabaseCollection());
        endpointContext.set(endpoint);
        try {
            return request.execute(this, endpoint.getDatabaseCollection());
        } finally {
            endpointContext.remove();
        }
    }

    /**
     * Executes CREATE operation
     */
    public WriteResponse executeCreate(CreateRequest request, String tableName) {
        Endpoint endpoint = requireEndpointContext();
        List<DynamicDocument> documents = request.getDocuments().stream()
                .map(doc -> {
                    Map<String, Object> sanitized = sanitizeDocumentForWrite(doc);
                    Map<String, Object> processed = subEntityProcessor.prepareForCreate(sanitized, endpoint.getSubEntities());
                    DynamicDocument dynamicDoc = new DynamicDocument(tableName, processed);
                    dynamicDoc.setLatestRequestId(request.getRequestId());
                    dynamicDoc.setDeleted(false);
                    return dynamicDoc;
                })
                .collect(Collectors.toList());

        List<Long> insertedIds;
        if (request.isBulk()) {
            insertedIds = repository.insertMany(tableName, documents);
        } else {
            insertedIds = List.of(repository.insertOne(tableName, documents.get(0)));
        }

        List<Map<String, Object>> responseDocs = repository.findByIds(tableName, insertedIds);
        String message = request.isBulk()
                ? "Documents created successfully."
                : "Document created successfully.";
        List<String> stringIds = insertedIds.stream()
                .map(String::valueOf)
                .collect(Collectors.toList());
        return new CreateResponse(stringIds, responseDocs, message);
    }

    /**
     * Executes UPDATE operation
     */
    public WriteResponse executeUpdate(UpdateRequest request, String tableName) {
        Endpoint endpoint = requireEndpointContext();
        FilterResult filterResult = translateFilter(request.getFilter());
        Map<String, Object> updates = sanitizeDocumentForWrite(request.getUpdates());
        List<DynamicDocument> matchingDocuments = repository.findDocuments(
                tableName,
                filterResult.getWhereClause(),
                filterResult.getParameters(),
                null
        );

        if (matchingDocuments.isEmpty()) {
            return new UpdateResponse(0, 0, List.of(), "No documents matched the provided filter.");
        }

        applySubEntityUpdates(endpoint.getSubEntities(), updates, request.isUpdateMultiple(),
                () -> loadSingleDocument(tableName, filterResult));

        List<Map<String, Object>> matchingMaps = matchingDocuments.stream()
                .map(DynamicDocument::toMap)
                .collect(Collectors.toList());

        DocumentChangeResult changeResult =
                documentChangeDetector.evaluate(matchingMaps, updates);

        if (!changeResult.hasChanges()) {
            return new UpdateResponse(matchingDocuments.size(), 0, matchingMaps,
                    "No changes detected; documents remain unchanged.");
        }

        Map<String, Object> effectiveUpdates = new LinkedHashMap<>(updates);
        effectiveUpdates.put("latestRequestId", request.getRequestId());

        int modifiedCount = repository.update(
                tableName,
                filterResult.getWhereClause(),
                effectiveUpdates,
                filterResult.getParameters(),
                request.isUpdateMultiple()
        );

        List<Long> ids = matchingDocuments.stream()
                .map(DynamicDocument::getId)
                .collect(Collectors.toList());
        List<Map<String, Object>> updatedDocuments = repository.findByIds(tableName, ids);

        return new UpdateResponse(matchingDocuments.size(), modifiedCount, updatedDocuments,
                "Documents updated successfully.");
    }

    /**
     * Executes DELETE operation (logical delete)
     */
    public WriteResponse executeDelete(DeleteRequest request, String tableName) {
        FilterResult filterResult = translateFilter(request.getFilter());

        List<DynamicDocument> documentsBeforeDelete = repository.findDocuments(
                tableName,
                filterResult.getWhereClause(),
                filterResult.getParameters(),
                null
        );

        if (documentsBeforeDelete.isEmpty()) {
            return new DeleteResponse(0, List.of(), "No documents matched the provided filter.");
        }

        int deletedCount = repository.delete(
                tableName,
                filterResult.getWhereClause(),
                filterResult.getParameters(),
                request.isDeleteMultiple(),
                request.getRequestId()
        );

        List<Long> ids = documentsBeforeDelete.stream()
                .map(DynamicDocument::getId)
                .collect(Collectors.toList());
        List<Map<String, Object>> deletedDocuments = repository.findByIds(tableName, ids);

        return new DeleteResponse(deletedCount, deletedDocuments, "Documents marked as deleted.");
    }

    /**
     * Executes UPSERT operation.
     * Delegates to sub-entity aware or simple path based on endpoint configuration.
     */
    public WriteResponse executeUpsert(UpsertRequest request, String tableName) {
        Endpoint endpoint = requireEndpointContext();
        FilterResult filterResult = translateFilter(request.getFilter());
        Map<String, Object> document = sanitizeDocumentForWrite(request.getDocument());
        document.put("latestRequestId", request.getRequestId());

        Set<String> subEntities = endpoint.getSubEntities();
        if (!subEntities.isEmpty()) {
            return upsertWithSubEntities(tableName, filterResult, document, subEntities, request.getRequestId());
        }
        return upsertSimple(tableName, filterResult, document);
    }

    private WriteResponse upsertWithSubEntities(String tableName, FilterResult filterResult,
                                                 Map<String, Object> document, Set<String> subEntities,
                                                 String requestId) {
        DynamicDocument existingDoc = findFirstDocument(tableName, filterResult);
        if (existingDoc != null) {
            return upsertUpdateExisting(tableName, filterResult, document, subEntities, existingDoc, requestId);
        }
        return upsertInsertNew(tableName, filterResult, document, subEntities);
    }

    private WriteResponse upsertUpdateExisting(String tableName, FilterResult filterResult,
                                                Map<String, Object> document, Set<String> subEntities,
                                                DynamicDocument existingDoc, String requestId) {
        Map<String, Object> existingMap = existingDoc.toMap();
        Map<String, Object> updates = new LinkedHashMap<>(document);
        subEntityProcessor.applyUpsertUpdate(updates, subEntities, existingMap);

        DocumentChangeResult changeResult = documentChangeDetector.evaluate(List.of(existingMap), updates);
        String documentId = String.valueOf(existingDoc.getId());

        if (!changeResult.hasChanges()) {
            return new UpsertResponse(false, documentId, 1, 0,
                    List.of(existingMap), "No changes detected; document remains unchanged.");
        }

        Map<String, Object> effectiveUpdates = new LinkedHashMap<>(updates);
        effectiveUpdates.put("latestRequestId", requestId);
        repository.update(tableName, filterResult.getWhereClause(), effectiveUpdates, filterResult.getParameters(), false);

        List<Map<String, Object>> updatedDocuments = repository.findByIds(tableName, List.of(existingDoc.getId()));
        return new UpsertResponse(false, documentId, 1L, 1L, updatedDocuments, "Document updated successfully.");
    }

    private WriteResponse upsertInsertNew(String tableName, FilterResult filterResult,
                                           Map<String, Object> document, Set<String> subEntities) {
        Map<String, Object> processed = subEntityProcessor.prepareForUpsertCreate(document, subEntities);
        Map<String, Object> result = repository.upsert(tableName, filterResult.getWhereClause(),
                processed, filterResult.getParameters());
        return buildUpsertResponse(tableName, filterResult, result);
    }

    private WriteResponse upsertSimple(String tableName, FilterResult filterResult, Map<String, Object> document) {
        document.put("isDeleted", false);
        DynamicDocument existingDoc = findFirstDocument(tableName, filterResult);
        String existingDocumentId = existingDoc != null ? String.valueOf(existingDoc.getId()) : null;

        Map<String, Object> result = repository.upsert(tableName, filterResult.getWhereClause(),
                document, filterResult.getParameters());

        boolean wasInserted = result.containsKey("upsertedId");
        String documentId = wasInserted ? String.valueOf(result.get("upsertedId")) : existingDocumentId;
        List<Map<String, Object>> documents = loadUpsertedDocuments(tableName, filterResult, documentId);

        return new UpsertResponse(wasInserted, documentId,
                extractCount(result, "matchedCount"), extractCount(result, "modifiedCount"),
                documents, wasInserted ? "Document inserted successfully." : "Document updated successfully.");
    }

    private WriteResponse buildUpsertResponse(String tableName, FilterResult filterResult, Map<String, Object> result) {
        boolean wasInserted = result.containsKey("upsertedId");
        String documentId = wasInserted ? String.valueOf(result.get("upsertedId")) : null;
        List<Map<String, Object>> documents = loadUpsertedDocuments(tableName, filterResult, documentId);

        if (documentId == null) {
            documentId = extractDocumentId(documents);
        }

        return new UpsertResponse(wasInserted, documentId,
                extractCount(result, "matchedCount"), extractCount(result, "modifiedCount"),
                documents, wasInserted ? "Document inserted successfully." : "Document updated successfully.");
    }

    private String extractDocumentId(List<Map<String, Object>> documents) {
        if (documents == null || documents.isEmpty()) {
            return null;
        }

        Object id = documents.get(0).get("id");
        return id != null ? String.valueOf(id) : null;
    }

    private DynamicDocument findFirstDocument(String tableName, FilterResult filterResult) {
        List<DynamicDocument> docs = repository.findDocuments(tableName, filterResult.getWhereClause(),
                filterResult.getParameters(), 1);
        return docs.isEmpty() ? null : docs.get(0);
    }

    private List<Map<String, Object>> loadUpsertedDocuments(String tableName, FilterResult filterResult, String documentId) {
        if (documentId != null) {
            return repository.findByIds(tableName, List.of(Long.parseLong(documentId)));
        }
        List<DynamicDocument> refreshed = repository.findDocuments(tableName, filterResult.getWhereClause(),
                filterResult.getParameters(), 1);
        return refreshed.stream().map(DynamicDocument::toMap).collect(Collectors.toList());
    }

    private long extractCount(Map<String, Object> result, String key) {
        return result.containsKey(key) ? ((Number) result.get(key)).longValue() : 0;
    }

    private FilterResult translateFilter(Map<String, Object> filter) {
        sigma.model.filter.FilterRequest filterRequest = new sigma.model.filter.FilterRequest(filter, null);
        return filterTranslator.translate(filterRequest);
    }

    private static final Set<String> RESERVED_WRITE_FIELDS = Set.of("isDeleted", "latestRequestId");

    private Map<String, Object> sanitizeDocumentForWrite(Map<String, Object> source) {
        Map<String, Object> sanitized = new LinkedHashMap<>();
        if (source == null) {
            return sanitized;
        }

        source.forEach((key, value) -> {
            if (key != null && !RESERVED_WRITE_FIELDS.contains(key)) {
                sanitized.put(key, value);
            }
        });

        return sanitized;
    }

    private void applySubEntityUpdates(Set<String> subEntities,
                                       Map<String, Object> updates,
                                       boolean updateMultiple,
                                       Supplier<Map<String, Object>> existingDocumentSupplier) {
        subEntityProcessor.applyUpdateOperations(updates, subEntities, updateMultiple, existingDocumentSupplier);
    }

    private Map<String, Object> loadSingleDocument(String tableName, FilterResult filterResult) {
        List<DynamicDocument> documents = repository.findDocuments(
                tableName,
                filterResult.getWhereClause(),
                filterResult.getParameters(),
                2
        );
        return ensureSingleDocument(documents);
    }

    private Map<String, Object> ensureSingleDocument(List<DynamicDocument> documents) {
        if (documents == null || documents.isEmpty()) {
            throw new IllegalArgumentException("No document found matching filter for sub-entity update");
        }
        if (documents.size() > 1) {
            throw new IllegalArgumentException("Multiple documents match filter; cannot apply sub-entity operations safely");
        }
        return documents.get(0).toMap();
    }
}
