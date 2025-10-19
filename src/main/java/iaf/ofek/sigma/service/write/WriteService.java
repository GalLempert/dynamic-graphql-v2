package iaf.ofek.sigma.service.write;

import com.mongodb.client.result.UpdateResult;
import iaf.ofek.sigma.dto.request.*;
import iaf.ofek.sigma.dto.response.*;
import iaf.ofek.sigma.filter.FilterTranslator;
import iaf.ofek.sigma.model.DynamicDocument;
import iaf.ofek.sigma.model.Endpoint;
import iaf.ofek.sigma.persistence.repository.DynamicMongoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * Service for executing write operations with ACID transaction support
 *
 * Responsibilities:
 * - Execute CREATE, UPDATE, DELETE, UPSERT operations
 * - Inject audit fields automatically
 * - Translate filters to MongoDB queries
 * - Call repository layer
 * - Ensure ACID properties via @Transactional
 *
 * All write methods are transactional - if any operation fails, the entire
 * transaction will be rolled back to maintain data consistency.
 *
 * Note: MongoDB transactions require MongoDB 4.0+ with replica set configuration
 */
@Service
@Transactional  // All methods in this service run within a transaction by default
public class WriteService {

    private static final Logger logger = LoggerFactory.getLogger(WriteService.class);

    private final DynamicMongoRepository repository;
    private final FilterTranslator filterTranslator;
    private final SubEntityProcessor subEntityProcessor;

    public WriteService(DynamicMongoRepository repository,
                        FilterTranslator filterTranslator,
                        SubEntityProcessor subEntityProcessor) {
        this.repository = repository;
        this.filterTranslator = filterTranslator;
        this.subEntityProcessor = subEntityProcessor;
    }

    /**
     * Executes a write request
     * Uses polymorphism - ZERO switch statements!
     */
    public WriteResponse execute(WriteRequest request, Endpoint endpoint) {
        logger.info("Executing {} operation on collection: {}", request.getType(), endpoint.getDatabaseCollection());
        return request.execute(this, endpoint.getDatabaseCollection(), endpoint);
    }

    /**
     * Executes CREATE operation
     * Made public for Template Method pattern
     * Audit fields are automatically populated by Spring Data
     */
    public WriteResponse executeCreate(CreateRequest request, String collectionName, Endpoint endpoint) {
        // Convert Map to DynamicDocument and set request ID
        List<DynamicDocument> documents = request.getDocuments().stream()
                .map(doc -> prepareDocumentForCreate(doc, request.getRequestId(), endpoint))
                .collect(Collectors.toList());

        if (request.isBulk()) {
            List<String> insertedIds = repository.insertMany(collectionName, documents);
            return new CreateResponse(insertedIds);
        } else {
            String insertedId = repository.insertOne(collectionName, documents.get(0));
            return new CreateResponse(List.of(insertedId));
        }
    }

    /**
     * Executes UPDATE operation
     * Made public for Template Method pattern
     * lastModifiedAt and lastModifiedBy are automatically updated by Spring Data
     */
    public WriteResponse executeUpdate(UpdateRequest request, String collectionName, Endpoint endpoint) {
        // Translate filter to MongoDB query
        iaf.ofek.sigma.model.filter.FilterRequest filterRequest =
                new iaf.ofek.sigma.model.filter.FilterRequest(request.getFilter(), null);
        Query query = filterTranslator.translate(filterRequest);
        query.addCriteria(Criteria.where("isDeleted").ne(true));

        // Add latestRequestId to updates
        Map<String, Object> updates = request.getUpdates() != null
                ? new HashMap<>(request.getUpdates())
                : new HashMap<>();
        updates.remove("isDeleted");

        Set<String> subEntities = endpoint.getSubEntities();
        Set<String> subEntityFields = new HashSet<>();
        for (String key : updates.keySet()) {
            if (subEntities.contains(key)) {
                subEntityFields.add(key);
            }
        }

        Map<String, Object> baseUpdates = new HashMap<>();
        for (Map.Entry<String, Object> entry : updates.entrySet()) {
            if (!subEntityFields.contains(entry.getKey())) {
                baseUpdates.put(entry.getKey(), entry.getValue());
            }
        }
        baseUpdates.put("latestRequestId", request.getRequestId());

        if (subEntityFields.isEmpty()) {
            // Execute update - Spring Data will auto-update lastModifiedAt/lastModifiedBy
            UpdateResult result = repository.update(
                    collectionName,
                    query,
                    baseUpdates,
                    request.isUpdateMultiple()
            );

            if (result.getMatchedCount() == 0) {
                throw new IllegalArgumentException("No matching documents found for update");
            }

            return new UpdateResponse(result.getMatchedCount(), result.getModifiedCount());
        }

        List<org.bson.Document> existingDocuments = repository.findWithQuery(collectionName, query);
        if (existingDocuments.isEmpty()) {
            throw new IllegalArgumentException("No matching documents found for update");
        }

        if (!request.isUpdateMultiple() && existingDocuments.size() > 1) {
            existingDocuments = List.of(existingDocuments.get(0));
        }

        long matched = 0;
        long modified = 0;

        for (org.bson.Document existing : existingDocuments) {
            Map<String, Object> updateForDocument = new HashMap<>(baseUpdates);
            for (String field : subEntityFields) {
                Object operations = updates.get(field);
                List<Map<String, Object>> merged = subEntityProcessor.applyOperations(
                        existing.get(field),
                        operations,
                        field
                );
                updateForDocument.put(field, merged);
            }

            Query idQuery = Query.query(Criteria.where("_id").is(existing.get("_id")));
            idQuery.addCriteria(Criteria.where("isDeleted").ne(true));

            UpdateResult result = repository.update(
                    collectionName,
                    idQuery,
                    updateForDocument,
                    false
            );

            matched += result.getMatchedCount();
            modified += result.getModifiedCount();
        }

        return new UpdateResponse(matched, modified);
    }

    /**
     * Executes DELETE operation
     * Made public for Template Method pattern
     */
    public WriteResponse executeDelete(DeleteRequest request, String collectionName, Endpoint endpoint) {
        // Translate filter to MongoDB query
        iaf.ofek.sigma.model.filter.FilterRequest filterRequest =
                new iaf.ofek.sigma.model.filter.FilterRequest(request.getFilter(), null);
        Query query = filterTranslator.translate(filterRequest);
        query.addCriteria(Criteria.where("isDeleted").ne(true));

        // Execute logical delete
        UpdateResult result = repository.logicalDelete(
                collectionName,
                query,
                request.isDeleteMultiple(),
                request.getRequestId()
        );

        if (result.getMatchedCount() == 0) {
            throw new IllegalArgumentException("No matching documents found for delete");
        }

        return new DeleteResponse(result.getModifiedCount());
    }

    /**
     * Executes UPSERT operation
     * Made public for Template Method pattern
     * Audit fields automatically managed by Spring Data (createdAt if insert, lastModifiedAt if update)
     */
    public WriteResponse executeUpsert(UpsertRequest request, String collectionName, Endpoint endpoint) {
        // Translate filter to MongoDB query
        iaf.ofek.sigma.model.filter.FilterRequest filterRequest =
                new iaf.ofek.sigma.model.filter.FilterRequest(request.getFilter(), null);
        Query query = filterTranslator.translate(filterRequest);
        query.addCriteria(Criteria.where("isDeleted").ne(true));

        Map<String, Object> document = request.getDocument() != null
                ? new HashMap<>(request.getDocument())
                : new HashMap<>();
        document.remove("isDeleted");

        Set<String> subEntities = endpoint.getSubEntities();
        Set<String> subEntityFields = new HashSet<>();
        for (String key : document.keySet()) {
            if (subEntities.contains(key)) {
                subEntityFields.add(key);
            }
        }

        Map<String, Object> baseDocument = new HashMap<>();
        for (Map.Entry<String, Object> entry : document.entrySet()) {
            if (!subEntityFields.contains(entry.getKey())) {
                baseDocument.put(entry.getKey(), entry.getValue());
            }
        }
        baseDocument.put("latestRequestId", request.getRequestId());

        if (subEntityFields.isEmpty()) {
            UpdateResult result = repository.upsert(collectionName, query, baseDocument);

            boolean wasInserted = result.getUpsertedId() != null;
            String documentId = wasInserted && result.getUpsertedId().isObjectId()
                    ? result.getUpsertedId().asObjectId().getValue().toString()
                    : null;

            return new UpsertResponse(
                    wasInserted,
                    documentId,
                    result.getMatchedCount(),
                    result.getModifiedCount()
            );
        }

        List<org.bson.Document> existingDocuments = repository.findWithQuery(collectionName, query);

        if (existingDocuments.isEmpty()) {
            for (String field : subEntityFields) {
                Object value = document.get(field);
                Map<String, Object> temp = new HashMap<>();
                temp.put(field, value);
                subEntityProcessor.prepareForCreate(temp, Set.of(field));
                Object normalized = temp.get(field);
                baseDocument.put(field, normalized);
            }
            baseDocument.put("isDeleted", Boolean.FALSE);

            UpdateResult result = repository.upsert(collectionName, query, baseDocument);
            boolean wasInserted = result.getUpsertedId() != null;
            String documentId = wasInserted && result.getUpsertedId().isObjectId()
                    ? result.getUpsertedId().asObjectId().getValue().toString()
                    : null;

            return new UpsertResponse(
                    wasInserted,
                    documentId,
                    result.getMatchedCount(),
                    result.getModifiedCount()
            );
        }

        long matched = 0;
        long modified = 0;

        for (org.bson.Document existing : existingDocuments) {
            Map<String, Object> updateForDocument = new HashMap<>(baseDocument);
            for (String field : subEntityFields) {
                Object operations = document.get(field);
                List<Map<String, Object>> merged = subEntityProcessor.applyOperations(
                        existing.get(field),
                        operations,
                        field
                );
                updateForDocument.put(field, merged);
            }

            Query idQuery = Query.query(Criteria.where("_id").is(existing.get("_id")));
            idQuery.addCriteria(Criteria.where("isDeleted").ne(true));

            UpdateResult result = repository.update(
                    collectionName,
                    idQuery,
                    updateForDocument,
                    false
            );

            matched += result.getMatchedCount();
            modified += result.getModifiedCount();
        }

        return new UpsertResponse(false, null, matched, modified);
    }

    private DynamicDocument prepareDocumentForCreate(Map<String, Object> original,
                                                     String requestId,
                                                     Endpoint endpoint) {
        Map<String, Object> document = original != null ? new HashMap<>(original) : new HashMap<>();
        document.remove("isDeleted");

        if (!endpoint.getSubEntities().isEmpty()) {
            subEntityProcessor.prepareForCreate(document, endpoint.getSubEntities());
        }

        DynamicDocument dynamicDoc = new DynamicDocument(document);
        dynamicDoc.setLatestRequestId(requestId);
        dynamicDoc.setDeleted(false);
        return dynamicDoc;
    }
}
