package iaf.ofek.sigma.service.write;

import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import iaf.ofek.sigma.dto.request.*;
import iaf.ofek.sigma.dto.response.*;
import iaf.ofek.sigma.filter.FilterTranslator;
import iaf.ofek.sigma.model.DynamicDocument;
import iaf.ofek.sigma.model.Endpoint;
import iaf.ofek.sigma.persistence.repository.DynamicMongoRepository;
import iaf.ofek.sigma.service.write.subentity.SubEntityProcessor;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final ThreadLocal<Endpoint> endpointContext = new ThreadLocal<>();

    public WriteService(DynamicMongoRepository repository,
                        FilterTranslator filterTranslator,
                        SubEntityProcessor subEntityProcessor) {
        this.repository = repository;
        this.filterTranslator = filterTranslator;
        this.subEntityProcessor = subEntityProcessor;
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

        if (request.isBulk()) {
            List<String> insertedIds = repository.insertMany(collectionName, documents);
            return new CreateResponse(insertedIds);
        }

        String insertedId = repository.insertOne(collectionName, documents.get(0));
        return new CreateResponse(List.of(insertedId));
    }

    /**
     * Executes UPDATE operation
     */
    public WriteResponse executeUpdate(UpdateRequest request, String collectionName) {
        Endpoint endpoint = requireEndpointContext();

        iaf.ofek.sigma.model.filter.FilterRequest filterRequest =
                new iaf.ofek.sigma.model.filter.FilterRequest(request.getFilter(), null);
        Query query = filterTranslator.translate(filterRequest);

        Map<String, Object> updates = sanitizeDocumentForWrite(request.getUpdates());
        applySubEntityUpdates(endpoint.getSubEntities(), updates, request.isUpdateMultiple(),
                () -> loadSingleDocument(collectionName, query));
        updates.put("latestRequestId", request.getRequestId());

        UpdateResult result = repository.update(collectionName, query, updates, request.isUpdateMultiple());
        return new UpdateResponse(result.getMatchedCount(), result.getModifiedCount());
    }

    /**
     * Executes DELETE operation (logical delete)
     */
    public WriteResponse executeDelete(DeleteRequest request, String collectionName) {

        iaf.ofek.sigma.model.filter.FilterRequest filterRequest =
                new iaf.ofek.sigma.model.filter.FilterRequest(request.getFilter(), null);
        Query query = filterTranslator.translate(filterRequest);

        DeleteResult result = repository.delete(collectionName, query, request.isDeleteMultiple(), request.getRequestId());
        return new DeleteResponse(result.getDeletedCount());
    }

    /**
     * Executes UPSERT operation
     */
    public WriteResponse executeUpsert(UpsertRequest request, String collectionName) {
        Endpoint endpoint = requireEndpointContext();

        iaf.ofek.sigma.model.filter.FilterRequest filterRequest =
                new iaf.ofek.sigma.model.filter.FilterRequest(request.getFilter(), null);
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

                UpdateResult updateResult = repository.update(collectionName, query, updates, false);
                String documentId = extractDocumentId(existingDoc);
                return new UpsertResponse(false, documentId, updateResult.getMatchedCount(), updateResult.getModifiedCount());
            }

            Map<String, Object> processed = subEntityProcessor.prepareForUpsertCreate(document, subEntities);
            UpdateResult result = repository.upsert(collectionName, query, processed);

            boolean wasInserted = result.getUpsertedId() != null;
            String documentId = wasInserted
                    ? result.getUpsertedId().asObjectId().getValue().toString()
                    : null;

            return new UpsertResponse(wasInserted, documentId, result.getMatchedCount(), result.getModifiedCount());
        }

        document.put("isDeleted", false);
        UpdateResult result = repository.upsert(collectionName, query, document);

        boolean wasInserted = result.getUpsertedId() != null;
        String documentId = wasInserted
                ? result.getUpsertedId().asObjectId().getValue().toString()
                : null;

        return new UpsertResponse(wasInserted, documentId, result.getMatchedCount(), result.getModifiedCount());
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
}
