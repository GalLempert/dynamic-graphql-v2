package iaf.ofek.sigma.service.write;

import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import iaf.ofek.sigma.dto.request.*;
import iaf.ofek.sigma.dto.response.*;
import iaf.ofek.sigma.filter.FilterTranslator;
import iaf.ofek.sigma.model.DynamicDocument;
import iaf.ofek.sigma.model.Endpoint;
import iaf.ofek.sigma.persistence.repository.DynamicMongoRepository;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
    private final ThreadLocal<Endpoint> endpointContext = new ThreadLocal<>();

    public WriteService(DynamicMongoRepository repository, FilterTranslator filterTranslator) {
        this.repository = repository;
        this.filterTranslator = filterTranslator;
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
        Set<String> subEntities = endpoint.getSubEntities();

        List<DynamicDocument> documents = request.getDocuments().stream()
                .map(doc -> {
                    Map<String, Object> sanitized = sanitizeDocumentForWrite(doc);
                    Map<String, Object> processed = processSubEntitiesForCreate(sanitized, subEntities);
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
        normalizeSubEntityUpdates(collectionName, query, updates, endpoint.getSubEntities(), request.isUpdateMultiple(), null);
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
                if (existingDocs.size() > 1) {
                    throw new IllegalArgumentException("Multiple documents match filter; cannot upsert sub-entities safely");
                }

                Document existingDoc = existingDocs.get(0);
                Map<String, Object> updates = new java.util.HashMap<>(document);
                normalizeSubEntityUpdates(collectionName, query, updates, subEntities, false, existingDoc);

                UpdateResult updateResult = repository.update(collectionName, query, updates, false);
                String documentId = extractDocumentId(existingDoc);
                return new UpsertResponse(false, documentId, updateResult.getMatchedCount(), updateResult.getModifiedCount());
            }

            Map<String, Object> processed = processSubEntitiesForCreate(document, subEntities);
            processed.put("isDeleted", false);
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

    private Map<String, Object> processSubEntitiesForCreate(Map<String, Object> document, Set<String> subEntityFields) {
        if (subEntityFields == null || subEntityFields.isEmpty()) {
            return new LinkedHashMap<>(document);
        }

        Map<String, Object> processed = new LinkedHashMap<>(document);

        for (String field : subEntityFields) {
            Object value = processed.get(field);
            if (value == null) {
                continue;
            }

            if (!(value instanceof List<?> listValue)) {
                throw new IllegalArgumentException("Sub-entity field '" + field + "' must be an array");
            }

            List<Map<String, Object>> normalized = new ArrayList<>();
            Set<String> existingIds = new java.util.HashSet<>();

            for (Object item : listValue) {
                if (!(item instanceof Map<?, ?> itemMap)) {
                    throw new IllegalArgumentException("Sub-entity field '" + field + "' must contain JSON objects");
                }

                Map<String, Object> entry = copyMap(itemMap);

                Object deleteFlag = removeKeyCaseInsensitive(entry, "isDelete");
                if (deleteFlag == null) {
                    deleteFlag = removeKeyCaseInsensitive(entry, "isDeleted");
                }
                if (deleteFlag != null && toBoolean(deleteFlag)) {
                    throw new IllegalArgumentException("Sub-entity create payload for '" + field + "' cannot mark entries as deleted");
                }

                Object idValue = removeKeyCaseInsensitive(entry, "myId");
                String myId = idValue != null ? idValue.toString() : null;
                if (myId == null || myId.isBlank() || existingIds.contains(myId)) {
                    myId = generateUniqueSubEntityId(existingIds);
                }
                existingIds.add(myId);

                entry.put("myId", myId);
                entry.put("isDeleted", false);
                normalized.add(entry);
            }

            processed.put(field, normalized);
        }

        return processed;
    }

    private void normalizeSubEntityUpdates(String collectionName,
                                           Query query,
                                           Map<String, Object> updates,
                                           Set<String> subEntityFields,
                                           boolean updateMultiple,
                                           Document existingDocumentOverride) {
        if (subEntityFields == null || subEntityFields.isEmpty()) {
            return;
        }

        Set<String> fieldsToProcess = updates.keySet().stream()
                .filter(subEntityFields::contains)
                .collect(Collectors.toSet());

        if (fieldsToProcess.isEmpty()) {
            return;
        }

        if (updateMultiple) {
            throw new IllegalArgumentException("Sub-entity updates require updateMultiple=false");
        }

        Document existingDocument = existingDocumentOverride;
        if (existingDocument == null) {
            List<Document> documents = repository.findWithQuery(collectionName, query);
            if (documents.isEmpty()) {
                throw new IllegalArgumentException("No document found matching filter for sub-entity update");
            }
            if (documents.size() > 1) {
                throw new IllegalArgumentException("Multiple documents match filter; cannot apply sub-entity operations safely");
            }
            existingDocument = documents.get(0);
        }

        for (String field : fieldsToProcess) {
            Object operations = updates.get(field);
            if (!(operations instanceof List<?> operationsList)) {
                throw new IllegalArgumentException("Sub-entity field '" + field + "' must be an array of JSON objects");
            }

            List<Map<String, Object>> updated = applySubEntityOperations(field, operationsList, existingDocument.get(field));
            updates.put(field, updated);
        }
    }

    private List<Map<String, Object>> applySubEntityOperations(String fieldName,
                                                               List<?> operations,
                                                               Object currentValue) {
        List<Map<String, Object>> result = copyExistingSubEntities(fieldName, currentValue);
        Map<String, Map<String, Object>> byId = indexById(result);

        for (Object op : operations) {
            if (!(op instanceof Map<?, ?> opMap)) {
                throw new IllegalArgumentException("Sub-entity field '" + fieldName + "' must contain JSON objects");
            }

            Map<String, Object> operation = copyMap(opMap);

            Object idValue = removeKeyCaseInsensitive(operation, "myId");
            String myId = idValue != null ? idValue.toString() : null;

            Object deleteFlag = removeKeyCaseInsensitive(operation, "isDelete");
            if (deleteFlag == null) {
                deleteFlag = removeKeyCaseInsensitive(operation, "isDeleted");
            }
            boolean isDelete = deleteFlag != null && toBoolean(deleteFlag);

            if (myId == null || myId.isBlank()) {
                if (isDelete) {
                    throw new IllegalArgumentException("Cannot delete sub-entity without myId for field '" + fieldName + "'");
                }

                String newId = generateUniqueSubEntityId(byId.keySet());
                Map<String, Object> newEntry = new LinkedHashMap<>(operation);
                newEntry.put("myId", newId);
                newEntry.put("isDeleted", false);
                result.add(newEntry);
                byId.put(newId, newEntry);
                continue;
            }

            Map<String, Object> existing = byId.get(myId);
            if (existing == null || Boolean.TRUE.equals(existing.get("isDeleted"))) {
                throw new IllegalArgumentException("Sub-entity with myId '" + myId + "' does not exist or is deleted for field '" + fieldName + "'");
            }

            if (isDelete) {
                existing.put("isDeleted", true);
                continue;
            }

            operation.forEach(existing::put);
            existing.put("isDeleted", false);
            existing.put("myId", myId);
        }

        return result;
    }

    private List<Map<String, Object>> copyExistingSubEntities(String fieldName, Object value) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (value == null) {
            return result;
        }

        if (!(value instanceof List<?> listValue)) {
            throw new IllegalArgumentException("Existing data for sub-entity field '" + fieldName + "' is not an array");
        }

        for (Object item : listValue) {
            if (item instanceof Map<?, ?> map) {
                result.add(copyMap(map));
            } else {
                throw new IllegalArgumentException("Existing data for sub-entity field '" + fieldName + "' must be JSON objects");
            }
        }

        return result;
    }

    private Map<String, Map<String, Object>> indexById(List<Map<String, Object>> subEntities) {
        Map<String, Map<String, Object>> index = new LinkedHashMap<>();

        for (Map<String, Object> entry : subEntities) {
            Object idValue = entry.get("myId");
            String myId = idValue != null ? idValue.toString() : null;
            if (myId == null || myId.isBlank() || index.containsKey(myId)) {
                myId = generateUniqueSubEntityId(index.keySet());
                entry.put("myId", myId);
            }

            boolean deleted = Boolean.TRUE.equals(entry.get("isDeleted"));
            entry.put("isDeleted", deleted);

            index.put(myId, entry);
        }

        return index;
    }

    private Map<String, Object> copyMap(Map<?, ?> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            Object key = entry.getKey();
            if (key == null) {
                continue;
            }
            copy.put(key.toString(), entry.getValue());
        }
        return copy;
    }

    private Object removeKeyCaseInsensitive(Map<String, Object> map, String key) {
        for (String existingKey : new ArrayList<>(map.keySet())) {
            if (existingKey.equalsIgnoreCase(key)) {
                return map.remove(existingKey);
            }
        }
        return null;
    }

    private boolean toBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        if (value != null) {
            return Boolean.parseBoolean(value.toString());
        }
        return false;
    }

    private String generateUniqueSubEntityId(Collection<String> existingIds) {
        String id;
        do {
            id = UUID.randomUUID().toString();
        } while (existingIds != null && existingIds.contains(id));
        return id;
    }

    private String extractDocumentId(Document document) {
        if (document == null) {
            return null;
        }
        Object id = document.get("_id");
        return id != null ? id.toString() : null;
    }
}
