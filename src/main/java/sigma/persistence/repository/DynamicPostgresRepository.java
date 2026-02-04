package sigma.persistence.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import sigma.model.DynamicDocument;
import sigma.persistence.entity.SequenceCheckpoint;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Dynamic PostgreSQL repository for querying and writing to the dynamic_documents table
 * Uses JSONB for storing schemaless data with PostgreSQL-specific operators
 *
 * Supports:
 * - Read operations (queries with JSONB filtering, pagination)
 * - Write operations (insert, update, delete, upsert)
 */
@Repository
public class DynamicPostgresRepository {

    private static final Logger logger = LoggerFactory.getLogger(DynamicPostgresRepository.class);

    @PersistenceContext
    private EntityManager entityManager;

    private final DynamicDocumentJpaRepository jpaRepository;
    private final SequenceCheckpointRepository checkpointRepository;
    private final ObjectMapper objectMapper;

    public DynamicPostgresRepository(
            DynamicDocumentJpaRepository jpaRepository,
            SequenceCheckpointRepository checkpointRepository,
            ObjectMapper objectMapper) {
        this.jpaRepository = jpaRepository;
        this.checkpointRepository = checkpointRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Performs a select * query on the specified table (collection)
     * Used by FullCollectionRequest
     */
    public List<Map<String, Object>> findAll(String tableName) {
        logger.debug("Querying all documents from table: {}", tableName);
        List<DynamicDocument> documents = jpaRepository.findByTableNameAndNotDeleted(tableName);
        return documents.stream()
                .map(DynamicDocument::toMap)
                .collect(Collectors.toList());
    }

    /**
     * Finds documents using a complex query with filters, sorting, pagination, and projection
     * Used by FilteredQueryRequest
     *
     * @param tableName The logical table name
     * @param whereClause The WHERE clause for JSONB filtering (without "WHERE")
     * @param orderByClause The ORDER BY clause (without "ORDER BY")
     * @param limit Max results
     * @param offset Skip results
     * @param params Named parameters for the query
     * @return List of document maps
     */
    public List<Map<String, Object>> findWithQuery(
            String tableName,
            String whereClause,
            String orderByClause,
            Integer limit,
            Integer offset,
            Map<String, Object> params) {

        logger.debug("Executing query on table {}: WHERE {} ORDER BY {}", tableName, whereClause, orderByClause);

        StringBuilder jpql = new StringBuilder("SELECT d FROM DynamicDocument d WHERE d.tableName = :tableName AND d.isDeleted = false");

        if (whereClause != null && !whereClause.isEmpty()) {
            jpql.append(" AND ").append(whereClause);
        }

        if (orderByClause != null && !orderByClause.isEmpty()) {
            jpql.append(" ORDER BY ").append(orderByClause);
        }

        TypedQuery<DynamicDocument> query = entityManager.createQuery(jpql.toString(), DynamicDocument.class);
        query.setParameter("tableName", tableName);

        if (params != null) {
            params.forEach(query::setParameter);
        }

        if (offset != null && offset > 0) {
            query.setFirstResult(offset);
        }
        if (limit != null && limit > 0) {
            query.setMaxResults(limit);
        }

        List<DynamicDocument> documents = query.getResultList();
        return documents.stream()
                .map(DynamicDocument::toMap)
                .collect(Collectors.toList());
    }

    /**
     * Finds documents without applying the logical deletion filter. Intended for
     * operational flows that must inspect soft-deleted records (e.g. response
     * payloads after delete operations).
     */
    public List<Map<String, Object>> findRaw(String tableName, String whereClause, Map<String, Object> params) {
        StringBuilder jpql = new StringBuilder("SELECT d FROM DynamicDocument d WHERE d.tableName = :tableName");

        if (whereClause != null && !whereClause.isEmpty()) {
            jpql.append(" AND ").append(whereClause);
        }

        logger.debug("Executing raw query on table {}: {}", tableName, jpql);

        TypedQuery<DynamicDocument> query = entityManager.createQuery(jpql.toString(), DynamicDocument.class);
        query.setParameter("tableName", tableName);

        if (params != null) {
            params.forEach(query::setParameter);
        }

        List<DynamicDocument> documents = query.getResultList();
        return documents.stream()
                .map(DynamicDocument::toMap)
                .collect(Collectors.toList());
    }

    /**
     * Loads documents by their identifiers.
     *
     * @param tableName The table to query
     * @param ids Identifiers to load
     * @return Matching documents as maps
     */
    public List<Map<String, Object>> findByIds(String tableName, List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        logger.debug("Fetching {} documents by id from table {}", ids.size(), tableName);
        List<DynamicDocument> documents = jpaRepository.findByIdInAndTableName(ids, tableName);
        return documents.stream()
                .map(DynamicDocument::toMap)
                .collect(Collectors.toList());
    }

    /**
     * Finds nested documents by treating the specified field as a collection on its own.
     * Uses JSONB array expansion via jsonb_array_elements
     *
     * @param tableName The table hosting the nested documents
     * @param whereClause The WHERE clause for filtering
     * @param fatherDocumentPath Path of the nested document array inside the parent document
     * @param params Query parameters
     * @param orderByClause Order by clause
     * @param limit Limit
     * @param offset Offset
     * @return List of nested documents matching the query
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> findNestedDocuments(
            String tableName,
            String whereClause,
            String fatherDocumentPath,
            Map<String, Object> params,
            String orderByClause,
            Integer limit,
            Integer offset) {

        logger.debug("Executing nested query on table {} -> field {} with whereClause {}",
                tableName, fatherDocumentPath, whereClause);

        // For nested documents, we need to use native SQL with jsonb_array_elements
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT nested.value as nested_doc FROM dynamic_documents d, ");
        sql.append("jsonb_array_elements(d.data->'").append(fatherDocumentPath).append("') AS nested(value) ");
        sql.append("WHERE d.table_name = :tableName AND d.is_deleted = false ");

        if (whereClause != null && !whereClause.isEmpty()) {
            // Adjust whereClause for nested document context
            sql.append(" AND ").append(whereClause);
        }

        if (orderByClause != null && !orderByClause.isEmpty()) {
            sql.append(" ORDER BY ").append(orderByClause);
        }

        if (limit != null && limit > 0) {
            sql.append(" LIMIT ").append(limit);
        }

        if (offset != null && offset > 0) {
            sql.append(" OFFSET ").append(offset);
        }

        var nativeQuery = entityManager.createNativeQuery(sql.toString());
        nativeQuery.setParameter("tableName", tableName);

        if (params != null) {
            params.forEach(nativeQuery::setParameter);
        }

        List<Object> results = nativeQuery.getResultList();
        List<Map<String, Object>> nestedDocs = new ArrayList<>();

        for (Object result : results) {
            try {
                if (result instanceof String) {
                    Map<String, Object> doc = objectMapper.readValue((String) result, Map.class);
                    nestedDocs.add(doc);
                } else if (result instanceof Map) {
                    nestedDocs.add((Map<String, Object>) result);
                }
            } catch (JsonProcessingException e) {
                logger.error("Error parsing nested document JSON", e);
            }
        }

        return nestedDocs;
    }

    /**
     * Gets the next page of changes using sequence-based pagination
     * PostgreSQL doesn't have Change Streams like MongoDB, so we use a polling approach
     * based on the last_modified_at timestamp and sequence tracking
     */
    public Map<String, Object> getNextPageBySequence(String tableName, long startSequence, int batchSize) {
        logger.info("Getting next page for table: {}, startSequence: {}, batchSize: {}",
                tableName, startSequence, batchSize);

        // Get the checkpoint to find the last processed timestamp
        var checkpointOpt = checkpointRepository.findByCollectionName(tableName);
        Instant startTime = checkpointOpt
                .filter(cp -> cp.getSequence() == startSequence)
                .map(cp -> Instant.ofEpochMilli(cp.getLastUpdated()))
                .orElse(Instant.EPOCH);

        // Query for documents modified after the checkpoint
        String jpql = "SELECT d FROM DynamicDocument d WHERE d.tableName = :tableName " +
                "AND d.lastModifiedAt > :startTime ORDER BY d.lastModifiedAt ASC";

        TypedQuery<DynamicDocument> query = entityManager.createQuery(jpql, DynamicDocument.class);
        query.setParameter("tableName", tableName);
        query.setParameter("startTime", startTime);
        query.setMaxResults(batchSize);

        List<DynamicDocument> documents = query.getResultList();

        List<Map<String, Object>> results = new ArrayList<>();
        long currentSequence = startSequence;
        Instant lastModified = startTime;

        for (DynamicDocument doc : documents) {
            Map<String, Object> documentData = new HashMap<>();
            documentData.put("operationType", "update");
            documentData.put("documentKey", Map.of("_id", doc.getId()));
            documentData.put("fullDocument", doc.toMap());
            results.add(documentData);

            currentSequence++;
            if (doc.getLastModifiedAt() != null && doc.getLastModifiedAt().isAfter(lastModified)) {
                lastModified = doc.getLastModifiedAt();
            }
        }

        // Save checkpoint if we processed any changes
        if (!results.isEmpty()) {
            saveNewCheckpoint(tableName, lastModified, currentSequence);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("data", results);
        response.put("nextSequence", currentSequence);
        response.put("hasMore", !results.isEmpty() && results.size() == batchSize);

        return response;
    }

    private void saveNewCheckpoint(String tableName, Instant lastModified, long sequence) {
        SequenceCheckpoint checkpoint = new SequenceCheckpoint(tableName, sequence, lastModified.toEpochMilli() + "");
        checkpoint.setLastUpdated(lastModified.toEpochMilli());
        checkpointRepository.save(checkpoint);
        logger.debug("Saved checkpoint for table: {}, sequence: {}", tableName, sequence);
    }

    // ========== WRITE OPERATIONS ==========

    /**
     * Inserts a single document into the table
     * Uses DynamicDocument for automatic audit field management
     *
     * @param tableName The table name
     * @param document The DynamicDocument to insert
     * @return The ID of the inserted document
     */
    @Transactional
    public Long insertOne(String tableName, DynamicDocument document) {
        logger.info("Inserting document into table: {}", tableName);

        document.setTableName(tableName);
        DynamicDocument saved = jpaRepository.save(document);

        Long insertedId = saved.getId();
        logger.info("Successfully inserted document with id: {} (version: {})", insertedId, saved.getVersion());
        return insertedId;
    }

    /**
     * Inserts multiple documents into the table (bulk insert)
     * Uses DynamicDocument for automatic audit field management
     *
     * @param tableName The table name
     * @param documents The DynamicDocuments to insert
     * @return List of IDs of inserted documents
     */
    @Transactional
    public List<Long> insertMany(String tableName, List<DynamicDocument> documents) {
        logger.info("Inserting {} documents into table: {}", documents.size(), tableName);

        documents.forEach(doc -> doc.setTableName(tableName));
        List<DynamicDocument> savedDocs = jpaRepository.saveAll(documents);

        List<Long> insertedIds = savedDocs.stream()
                .map(DynamicDocument::getId)
                .collect(Collectors.toList());

        logger.info("Successfully inserted {} documents with audit fields", insertedIds.size());
        return insertedIds;
    }

    /**
     * Updates document(s) matching the query
     *
     * @param tableName The table name
     * @param whereClause The WHERE clause to find documents
     * @param updates The updates to apply (field name -> value)
     * @param params Query parameters
     * @param updateMultiple Whether to update all matching documents or just the first
     * @return Number of updated documents
     */
    @Transactional
    public int update(String tableName, String whereClause, Map<String, Object> updates,
                      Map<String, Object> params, boolean updateMultiple) {
        logger.info("Updating documents in table: {} (multiple: {})", tableName, updateMultiple);

        // First, find matching documents
        StringBuilder jpql = new StringBuilder("SELECT d FROM DynamicDocument d WHERE d.tableName = :tableName AND d.isDeleted = false");
        if (whereClause != null && !whereClause.isEmpty()) {
            jpql.append(" AND ").append(whereClause);
        }

        TypedQuery<DynamicDocument> query = entityManager.createQuery(jpql.toString(), DynamicDocument.class);
        query.setParameter("tableName", tableName);
        if (params != null) {
            params.forEach(query::setParameter);
        }
        if (!updateMultiple) {
            query.setMaxResults(1);
        }

        List<DynamicDocument> documents = query.getResultList();

        // Apply updates to each document
        for (DynamicDocument doc : documents) {
            Map<String, Object> dynamicFields = doc.getDynamicFields();
            if (dynamicFields == null) {
                dynamicFields = new HashMap<>();
            }
            dynamicFields.putAll(updates);
            doc.setDynamicFields(dynamicFields);
        }

        // Save all updated documents
        jpaRepository.saveAll(documents);

        logger.info("Update result: modified={}", documents.size());
        return documents.size();
    }

    /**
     * Upserts a document (update if exists, insert if not)
     *
     * @param tableName The table name
     * @param whereClause The WHERE clause to find the document
     * @param document The document data to upsert
     * @param params Query parameters
     * @return Map with "upsertedId" (if inserted) or "matchedCount"/"modifiedCount"
     */
    @Transactional
    public Map<String, Object> upsert(String tableName, String whereClause,
                                       Map<String, Object> document, Map<String, Object> params) {
        logger.info("Upserting document in table: {}", tableName);

        // Try to find existing document
        StringBuilder jpql = new StringBuilder("SELECT d FROM DynamicDocument d WHERE d.tableName = :tableName AND d.isDeleted = false");
        if (whereClause != null && !whereClause.isEmpty()) {
            jpql.append(" AND ").append(whereClause);
        }

        TypedQuery<DynamicDocument> query = entityManager.createQuery(jpql.toString(), DynamicDocument.class);
        query.setParameter("tableName", tableName);
        if (params != null) {
            params.forEach(query::setParameter);
        }
        query.setMaxResults(1);

        List<DynamicDocument> existing = query.getResultList();

        Map<String, Object> result = new HashMap<>();

        if (existing.isEmpty()) {
            // Insert new document
            DynamicDocument newDoc = new DynamicDocument(tableName, document);
            DynamicDocument saved = jpaRepository.save(newDoc);
            result.put("upsertedId", saved.getId());
            logger.info("Upserted new document with id: {}", saved.getId());
        } else {
            // Update existing document
            DynamicDocument doc = existing.get(0);
            Map<String, Object> dynamicFields = doc.getDynamicFields();
            if (dynamicFields == null) {
                dynamicFields = new HashMap<>();
            }
            dynamicFields.putAll(document);
            doc.setDynamicFields(dynamicFields);
            jpaRepository.save(doc);

            result.put("matchedCount", 1L);
            result.put("modifiedCount", 1L);
            logger.info("Updated existing document: matched=1, modified=1");
        }

        return result;
    }

    /**
     * Soft deletes document(s) matching the query
     *
     * @param tableName The table name
     * @param whereClause The WHERE clause to find documents to delete
     * @param params Query parameters
     * @param deleteMultiple Whether to delete all matching documents or just the first
     * @param requestId The request ID to record
     * @return Number of deleted documents
     */
    @Transactional
    public int delete(String tableName, String whereClause, Map<String, Object> params,
                      boolean deleteMultiple, String requestId) {
        logger.info("Deleting documents from table: {} (multiple: {})", tableName, deleteMultiple);

        // Find matching documents
        StringBuilder jpql = new StringBuilder("SELECT d FROM DynamicDocument d WHERE d.tableName = :tableName AND d.isDeleted = false");
        if (whereClause != null && !whereClause.isEmpty()) {
            jpql.append(" AND ").append(whereClause);
        }

        TypedQuery<DynamicDocument> query = entityManager.createQuery(jpql.toString(), DynamicDocument.class);
        query.setParameter("tableName", tableName);
        if (params != null) {
            params.forEach(query::setParameter);
        }
        if (!deleteMultiple) {
            query.setMaxResults(1);
        }

        List<DynamicDocument> documents = query.getResultList();

        // Soft delete each document
        for (DynamicDocument doc : documents) {
            doc.setDeleted(true);
            if (requestId != null) {
                doc.setLatestRequestId(requestId);
            }
        }

        jpaRepository.saveAll(documents);

        logger.info("Soft delete result: modified={}", documents.size());
        return documents.size();
    }

    /**
     * Finds documents matching the given criteria (for use with QueryBuilder results)
     */
    public List<DynamicDocument> findDocuments(String tableName, String whereClause,
                                                Map<String, Object> params, Integer limit) {
        StringBuilder jpql = new StringBuilder("SELECT d FROM DynamicDocument d WHERE d.tableName = :tableName AND d.isDeleted = false");
        if (whereClause != null && !whereClause.isEmpty()) {
            jpql.append(" AND ").append(whereClause);
        }

        TypedQuery<DynamicDocument> query = entityManager.createQuery(jpql.toString(), DynamicDocument.class);
        query.setParameter("tableName", tableName);
        if (params != null) {
            params.forEach(query::setParameter);
        }
        if (limit != null && limit > 0) {
            query.setMaxResults(limit);
        }

        return query.getResultList();
    }

    /**
     * Get a single document by ID
     */
    public DynamicDocument findById(String tableName, Long id) {
        return jpaRepository.findByIdAndTableName(id, tableName).orElse(null);
    }

    /**
     * Save a document (insert or update)
     */
    @Transactional
    public DynamicDocument save(DynamicDocument document) {
        return jpaRepository.save(document);
    }

    /**
     * Save multiple documents
     */
    @Transactional
    public List<DynamicDocument> saveAll(List<DynamicDocument> documents) {
        return jpaRepository.saveAll(documents);
    }
}
