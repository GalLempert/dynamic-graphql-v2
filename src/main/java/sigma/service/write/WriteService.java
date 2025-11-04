package sigma.service.write;

import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import sigma.dto.request.*;
import sigma.dto.response.*;
import sigma.filter.FilterTranslator;
import sigma.model.DynamicDocument;
import sigma.model.Endpoint;
import sigma.persistence.repository.DynamicMongoRepository;
import sigma.service.write.subentity.SubEntityProcessor;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.query.Query;
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

    private final DynamicMongoRepository repository;
    private final FilterTranslator filterTranslator;
    private final SubEntityProcessor subEntityProcessor;
    private final DocumentChangeDetector documentChangeDetector;
    private final ThreadLocal<Endpoint> endpointContext = new ThreadLocal<>();

    public WriteService(DynamicMongoRepository repository,
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
        logger.info("Executing {} operation on endpoint: {} -> collection: {}",
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
    public WriteResponse executeCreate(CreateRequest request, String collectionName) {
        Endpoint endpoint = requireEndpointContext();
        List<DynamicDocument> documents = request.getDocuments().stream()
                .map(doc -> {
                    Map<String, Object> sanitized = sanitizeDocumentForWrite(doc);
                    Map<String, Object> processed = subEntityProcessor.prepareForCreate(sanitized, endpoint.getSubEntities());
                    DynamicDocument dynamicDoc = new DynamicDocument(processed);
                    dynamicDoc.setLatestRequestId(request.getRequestId());
                    dynamicDoc.setDeleted(false);
                    return dynamicDoc;
                })
                .collect(Collectors.toList());

        List<String> insertedIds;
        if (request.isBulk()) {
            insertedIds = repository.insertMany(collectionName, documents);
        } else {
            insertedIds = List.of(repository.insertOne(collectionName, documents.get(0)));
        }

        List<Map<String, Object>> responseDocs = loadDocumentsByIds(collectionName, insertedIds);
        String message = request.isBulk()
                ? "Documents created successfully."
                : "Document created successfully.";
        return new CreateResponse(insertedIds, responseDocs, message);
    }

    /**
     * Executes UPDATE operation
     */
    public WriteResponse executeUpdate(UpdateRequest request, String collectionName) {
        Endpoint endpoint = requireEndpointContext();

        sigma.model.filter.FilterRequest filterRequest =
                new sigma.model.filter.FilterRequest(request.getFilter(), null);
        Query query = filterTranslator.translate(filterRequest);

        Map<String, Object> updates = sanitizeDocumentForWrite(request.getUpdates());
        List<Document> matchingDocuments = repository.findWithQuery(collectionName, query);

        if (matchingDocuments.isEmpty()) {
            return new UpdateResponse(0, 0, List.of(), "No documents matched the provided filter.");
        }

        applySubEntityUpdates(endpoint.getSubEntities(), updates, request.isUpdateMultiple(),
                () -> loadSingleDocument(collectionName, query));

        DocumentChangeResult changeResult =
                documentChangeDetector.evaluate(matchingDocuments, updates);

        if (!changeResult.hasChanges()) {
            List<Map<String, Object>> unchanged = convertDocuments(matchingDocuments);
            return new UpdateResponse(matchingDocuments.size(), 0, unchanged,
                    "No changes detected; documents remain unchanged.");
        }

        Map<String, Object> effectiveUpdates = new LinkedHashMap<>(updates);
        effectiveUpdates.put("latestRequestId", request.getRequestId());

        UpdateResult result = repository.update(collectionName, query, effectiveUpdates, request.isUpdateMultiple());

        List<Object> ids = extractIds(matchingDocuments);
        List<Map<String, Object>> updatedDocuments = convertDocuments(
                repository.findByIds(collectionName, ids));

        return new UpdateResponse(result.getMatchedCount(), result.getModifiedCount(), updatedDocuments,
                "Documents updated successfully.");
    }

    /**
     * Executes DELETE operation (logical delete)
     */
    public WriteResponse executeDelete(DeleteRequest request, String collectionName) {

        sigma.model.filter.FilterRequest filterRequest =
                new sigma.model.filter.FilterRequest(request.getFilter(), null);
        Query query = filterTranslator.translate(filterRequest);

        List<Document> documentsBeforeDelete = repository.findWithQuery(collectionName, query);
        if (documentsBeforeDelete.isEmpty()) {
            return new DeleteResponse(0, List.of(), "No documents matched the provided filter.");
        }

        DeleteResult result = repository.delete(collectionName, query, request.isDeleteMultiple(), request.getRequestId());

        List<Object> ids = extractIds(documentsBeforeDelete);
        List<Map<String, Object>> deletedDocuments = convertDocuments(
                repository.findByIds(collectionName, ids));

        return new DeleteResponse(result.getDeletedCount(), deletedDocuments, "Documents marked as deleted.");
    }

    /**
     * Executes UPSERT operation
     */
    public WriteResponse executeUpsert(UpsertRequest request, String collectionName) {
        Endpoint endpoint = requireEndpointContext();

        sigma.model.filter.FilterRequest filterRequest =
                new sigma.model.filter.FilterRequest(request.getFilter(), null);
        Query query = filterTranslator.translate(filterRequest);

        Map<String, Object> document = sanitizeDocumentForWrite(request.getDocument());
        document.put("latestRequestId", request.getRequestId());

        Set<String> subEntities = endpoint.getSubEntities();

        if (!subEntities.isEmpty()) {
            List<Document> existingDocs = repository.findWithQuery(collectionName, query);
            if (!existingDocs.isEmpty()) {
                Document existingDoc = ensureSingleDocument(existingDocs);
                Map<String, Object> updates = new LinkedHashMap<>(document);
                subEntityProcessor.applyUpsertUpdate(updates, subEntities, existingDoc);

                DocumentChangeResult changeResult =
                        documentChangeDetector.evaluate(List.of(existingDoc), updates);

                String documentId = extractDocumentId(existingDoc);

                if (!changeResult.hasChanges()) {
                    return new UpsertResponse(false, documentId, 1, 0,
                            convertDocuments(List.of(existingDoc)),
                            "No changes detected; document remains unchanged.");
                }

                Map<String, Object> effectiveUpdates = new LinkedHashMap<>(updates);
                effectiveUpdates.put("latestRequestId", request.getRequestId());

                UpdateResult updateResult = repository.update(collectionName, query, effectiveUpdates, false);

                List<Map<String, Object>> updatedDocuments = convertDocuments(
                        repository.findByIds(collectionName, List.of(documentId)));

                return new UpsertResponse(false, documentId, updateResult.getMatchedCount(), updateResult.getModifiedCount(),
                        updatedDocuments, "Document updated successfully.");
            }

            Map<String, Object> processed = subEntityProcessor.prepareForUpsertCreate(document, subEntities);
            UpdateResult result = repository.upsert(collectionName, query, processed);

            boolean wasInserted = result.getUpsertedId() != null;
            String documentId = wasInserted
                    ? result.getUpsertedId().asObjectId().getValue().toString()
                    : null;

            List<Map<String, Object>> documents = wasInserted
                    ? convertDocuments(repository.findByIds(collectionName, List.of(documentId)))
                    : List.of();

            String message = wasInserted
                    ? "Document inserted successfully."
                    : "Document updated successfully.";

            return new UpsertResponse(wasInserted, documentId, result.getMatchedCount(), result.getModifiedCount(),
                    documents, message);
        }

        document.put("isDeleted", false);

        List<Document> existingDocuments = repository.findWithQuery(collectionName, query);
        String existingDocumentId = extractDocumentIdFromQuery(existingDocuments);

        UpdateResult result = repository.upsert(collectionName, query, document);

        boolean wasInserted = result.getUpsertedId() != null;

        String documentId;
        List<Map<String, Object>> documents;

        if (wasInserted) {
            documentId = result.getUpsertedId().asObjectId().getValue().toString();
            documents = convertDocuments(repository.findByIds(collectionName, List.of(documentId)));
        } else if (existingDocumentId != null) {
            documentId = existingDocumentId;
            documents = convertDocuments(repository.findByIds(collectionName, List.of(documentId)));
        } else {
            List<Document> refreshed = repository.findWithQuery(collectionName, query);
            documentId = extractDocumentIdFromQuery(refreshed);
            documents = convertDocuments(refreshed);
        }

        String message = wasInserted
                ? "Document inserted successfully."
                : "Document updated successfully.";

        return new UpsertResponse(wasInserted, documentId, result.getMatchedCount(), result.getModifiedCount(),
                documents, message);
    }

    private Map<String, Object> sanitizeDocumentForWrite(Map<String, Object> source) {
        Map<String, Object> sanitized = new LinkedHashMap<>();
        if (source == null) {
            return sanitized;
        }

        source.forEach((key, value) -> {
            if (key == null) {
                return;
            }
            if ("isDeleted".equals(key) || "latestRequestId".equals(key)) {
                return;
            }
            sanitized.put(key, value);
        });

        return sanitized;
    }

    private String extractDocumentId(Document document) {
        if (document == null) {
            return null;
        }
        Object id = document.get("_id");
        return id != null ? id.toString() : null;
    }

    private String extractDocumentIdFromQuery(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return null;
        }
        return extractDocumentId(documents.get(0));
    }

    private void applySubEntityUpdates(Set<String> subEntities,
                                       Map<String, Object> updates,
                                       boolean updateMultiple,
                                       Supplier<Document> existingDocumentSupplier) {
        subEntityProcessor.applyUpdateOperations(updates, subEntities, updateMultiple, existingDocumentSupplier);
    }

    private Document loadSingleDocument(String collectionName, Query query) {
        List<Document> documents = repository.findWithQuery(collectionName, query);
        return ensureSingleDocument(documents);
    }

    private Document ensureSingleDocument(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            throw new IllegalArgumentException("No document found matching filter for sub-entity update");
        }
        if (documents.size() > 1) {
            throw new IllegalArgumentException("Multiple documents match filter; cannot apply sub-entity operations safely");
        }
        return documents.get(0);
    }

    private List<Map<String, Object>> convertDocuments(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }
        return documents.stream()
                .filter(Objects::nonNull)
                .map(doc -> new LinkedHashMap<String, Object>(doc))
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> loadDocumentsByIds(String collectionName, List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Object> objectIds = ids.stream()
                .map(id -> (Object) id)
                .collect(Collectors.toList());
        return convertDocuments(repository.findByIds(collectionName, objectIds));
    }

    private List<Object> extractIds(List<Document> documents) {
        return documents.stream()
                .map(doc -> doc.get("_id"))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
}
