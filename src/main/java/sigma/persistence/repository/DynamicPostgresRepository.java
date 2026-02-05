package sigma.persistence.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import sigma.model.DynamicDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Dynamic PostgreSQL repository for querying and writing to the dynamic_documents table
 * Uses JDBC with JSONB for storing schemaless data with PostgreSQL-specific operators
 *
 * Supports:
 * - Read operations (queries with JSONB filtering, pagination)
 * - Write operations (insert, update, delete, upsert)
 */
@Repository
public class DynamicPostgresRepository {

    private static final Logger logger = LoggerFactory.getLogger(DynamicPostgresRepository.class);

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final DynamicDocumentJpaRepository crudRepository;
    private final ObjectMapper objectMapper;

    public DynamicPostgresRepository(
            NamedParameterJdbcTemplate jdbcTemplate,
            DynamicDocumentJpaRepository crudRepository,
            ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.crudRepository = crudRepository;
        this.objectMapper = objectMapper;
    }

    private final RowMapper<DynamicDocument> documentRowMapper = (rs, rowNum) -> {
        DynamicDocument doc = new DynamicDocument();
        doc.setId(rs.getLong("id"));
        doc.setTableName(rs.getString("table_name"));
        doc.setVersion(rs.getLong("version"));
        doc.setDeleted(rs.getBoolean("is_deleted"));
        doc.setLatestRequestId(rs.getString("latest_request_id"));
        doc.setCreatedBy(rs.getString("created_by"));
        doc.setLastModifiedBy(rs.getString("last_modified_by"));
        doc.setSequenceNumber(rs.getLong("sequence_number"));

        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            doc.setCreatedAt(createdAt.toInstant());
        }
        Timestamp lastModifiedAt = rs.getTimestamp("last_modified_at");
        if (lastModifiedAt != null) {
            doc.setLastModifiedAt(lastModifiedAt.toInstant());
        }

        // Parse JSONB data column
        String dataJson = rs.getString("data");
        if (dataJson != null) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = objectMapper.readValue(dataJson, Map.class);
                doc.setDynamicFields(data);
            } catch (JsonProcessingException e) {
                logger.error("Error parsing JSONB data for document {}", doc.getId(), e);
                doc.setDynamicFields(new HashMap<>());
            }
        }

        return doc;
    };

    /**
     * Performs a select * query on the specified table (collection)
     * Used by FullCollectionRequest
     */
    public List<Map<String, Object>> findAll(String tableName) {
        logger.debug("Querying all documents from table: {}", tableName);
        List<DynamicDocument> documents = crudRepository.findByTableNameAndNotDeleted(tableName);
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

        StringBuilder sql = new StringBuilder("SELECT * FROM dynamic_documents WHERE table_name = :tableName AND is_deleted = false");

        if (whereClause != null && !whereClause.isEmpty()) {
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

        MapSqlParameterSource paramSource = new MapSqlParameterSource();
        paramSource.addValue("tableName", tableName);
        if (params != null) {
            params.forEach(paramSource::addValue);
        }

        List<DynamicDocument> documents = jdbcTemplate.query(sql.toString(), paramSource, documentRowMapper);
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
        StringBuilder sql = new StringBuilder("SELECT * FROM dynamic_documents WHERE table_name = :tableName");

        if (whereClause != null && !whereClause.isEmpty()) {
            sql.append(" AND ").append(whereClause);
        }

        logger.debug("Executing raw query on table {}: {}", tableName, sql);

        MapSqlParameterSource paramSource = new MapSqlParameterSource();
        paramSource.addValue("tableName", tableName);
        if (params != null) {
            params.forEach(paramSource::addValue);
        }

        List<DynamicDocument> documents = jdbcTemplate.query(sql.toString(), paramSource, documentRowMapper);
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
        List<DynamicDocument> documents = crudRepository.findByIdInAndTableName(ids, tableName);
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

        MapSqlParameterSource paramSource = new MapSqlParameterSource();
        paramSource.addValue("tableName", tableName);
        if (params != null) {
            params.forEach(paramSource::addValue);
        }

        List<Map<String, Object>> nestedDocs = new ArrayList<>();

        jdbcTemplate.query(sql.toString(), paramSource, (rs) -> {
            try {
                String nestedJson = rs.getString("nested_doc");
                if (nestedJson != null) {
                    Map<String, Object> doc = objectMapper.readValue(nestedJson, Map.class);
                    nestedDocs.add(doc);
                }
            } catch (JsonProcessingException e) {
                logger.error("Error parsing nested document JSON", e);
            }
        });

        return nestedDocs;
    }

    /**
     * Gets the next page of changes using sequence-based pagination.
     * Uses the sequence_number column which is automatically updated by a PostgreSQL trigger
     * on every insert/update operation.
     *
     * @param tableName The table name
     * @param startSequence The sequence number to start from (exclusive)
     * @param batchSize Maximum number of documents to return
     * @return Map containing "data" (list of change documents), "nextSequence", and "hasMore"
     */
    public Map<String, Object> getNextPageBySequence(String tableName, long startSequence, int batchSize) {
        logger.info("Getting next page for table: {}, startSequence: {}, batchSize: {}",
                tableName, startSequence, batchSize);

        // Query for documents with sequence_number greater than startSequence
        String sql = "SELECT * FROM dynamic_documents WHERE table_name = :tableName " +
                "AND sequence_number > :startSequence ORDER BY sequence_number ASC LIMIT :batchSize";

        MapSqlParameterSource paramSource = new MapSqlParameterSource();
        paramSource.addValue("tableName", tableName);
        paramSource.addValue("startSequence", startSequence);
        paramSource.addValue("batchSize", batchSize);

        List<DynamicDocument> documents = jdbcTemplate.query(sql, paramSource, documentRowMapper);

        List<Map<String, Object>> results = new ArrayList<>();
        long nextSequence = startSequence;

        for (DynamicDocument doc : documents) {
            Map<String, Object> documentData = new HashMap<>();
            documentData.put("operationType", doc.isDeleted() ? "delete" : "update");
            documentData.put("documentKey", Map.of("id", doc.getId()));
            documentData.put("fullDocument", doc.toMap());
            documentData.put("sequenceNumber", doc.getSequenceNumber());
            results.add(documentData);

            // Track the highest sequence number seen
            if (doc.getSequenceNumber() != null && doc.getSequenceNumber() > nextSequence) {
                nextSequence = doc.getSequenceNumber();
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("data", results);
        response.put("nextSequence", nextSequence);
        response.put("hasMore", !results.isEmpty() && results.size() == batchSize);

        return response;
    }

    // ========== WRITE OPERATIONS ==========

    /**
     * Inserts a single document into the table
     * Uses native SQL for full control over JSONB insertion
     *
     * @param tableName The table name
     * @param document The DynamicDocument to insert
     * @return The ID of the inserted document
     */
    @Transactional
    public Long insertOne(String tableName, DynamicDocument document) {
        logger.info("Inserting document into table: {}", tableName);

        document.setTableName(tableName);
        Instant now = Instant.now();
        if (document.getCreatedAt() == null) {
            document.setCreatedAt(now);
        }
        document.setLastModifiedAt(now);
        if (document.getVersion() == null) {
            document.setVersion(0L);
        }

        String sql = """
            INSERT INTO dynamic_documents (table_name, data, version, is_deleted, latest_request_id,
                created_by, last_modified_by, created_at, last_modified_at)
            VALUES (:tableName, :data::jsonb, :version, :isDeleted, :latestRequestId,
                :createdBy, :lastModifiedBy, :createdAt, :lastModifiedAt)
            RETURNING id
            """;

        MapSqlParameterSource params = buildInsertParams(document);

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(sql, params, keyHolder, new String[]{"id"});

        Long insertedId = keyHolder.getKey() != null ? keyHolder.getKey().longValue() : null;
        document.setId(insertedId);

        logger.info("Successfully inserted document with id: {} (version: {})", insertedId, document.getVersion());
        return insertedId;
    }

    /**
     * Inserts multiple documents into the table (bulk insert)
     *
     * @param tableName The table name
     * @param documents The DynamicDocuments to insert
     * @return List of IDs of inserted documents
     */
    @Transactional
    public List<Long> insertMany(String tableName, List<DynamicDocument> documents) {
        logger.info("Inserting {} documents into table: {}", documents.size(), tableName);

        List<Long> insertedIds = new ArrayList<>();
        for (DynamicDocument document : documents) {
            Long id = insertOne(tableName, document);
            insertedIds.add(id);
        }

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
        StringBuilder selectSql = new StringBuilder("SELECT * FROM dynamic_documents WHERE table_name = :tableName AND is_deleted = false");
        if (whereClause != null && !whereClause.isEmpty()) {
            selectSql.append(" AND ").append(whereClause);
        }
        if (!updateMultiple) {
            selectSql.append(" LIMIT 1");
        }

        MapSqlParameterSource selectParams = new MapSqlParameterSource();
        selectParams.addValue("tableName", tableName);
        if (params != null) {
            params.forEach(selectParams::addValue);
        }

        List<DynamicDocument> documents = jdbcTemplate.query(selectSql.toString(), selectParams, documentRowMapper);

        // Apply updates to each document
        Instant now = Instant.now();
        for (DynamicDocument doc : documents) {
            Map<String, Object> dynamicFields = doc.getDynamicFields();
            if (dynamicFields == null) {
                dynamicFields = new HashMap<>();
            }
            dynamicFields.putAll(updates);
            doc.setDynamicFields(dynamicFields);
            doc.setLastModifiedAt(now);
            doc.setVersion(doc.getVersion() != null ? doc.getVersion() + 1 : 1L);

            updateDocument(doc);
        }

        logger.info("Update result: modified={}", documents.size());
        return documents.size();
    }

    private void updateDocument(DynamicDocument document) {
        String sql = """
            UPDATE dynamic_documents
            SET data = :data::jsonb, version = :version, is_deleted = :isDeleted,
                latest_request_id = :latestRequestId, last_modified_by = :lastModifiedBy,
                last_modified_at = :lastModifiedAt
            WHERE id = :id
            """;

        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("id", document.getId());
        params.addValue("data", toJsonString(document.getDynamicFields()));
        params.addValue("version", document.getVersion());
        params.addValue("isDeleted", document.isDeleted());
        params.addValue("latestRequestId", document.getLatestRequestId());
        params.addValue("lastModifiedBy", document.getLastModifiedBy());
        params.addValue("lastModifiedAt", Timestamp.from(document.getLastModifiedAt()));

        jdbcTemplate.update(sql, params);
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
        StringBuilder selectSql = new StringBuilder("SELECT * FROM dynamic_documents WHERE table_name = :tableName AND is_deleted = false");
        if (whereClause != null && !whereClause.isEmpty()) {
            selectSql.append(" AND ").append(whereClause);
        }
        selectSql.append(" LIMIT 1");

        MapSqlParameterSource selectParams = new MapSqlParameterSource();
        selectParams.addValue("tableName", tableName);
        if (params != null) {
            params.forEach(selectParams::addValue);
        }

        List<DynamicDocument> existing = jdbcTemplate.query(selectSql.toString(), selectParams, documentRowMapper);

        Map<String, Object> result = new HashMap<>();

        if (existing.isEmpty()) {
            // Insert new document
            DynamicDocument newDoc = new DynamicDocument(tableName, document);
            Long insertedId = insertOne(tableName, newDoc);
            result.put("upsertedId", insertedId);
            logger.info("Upserted new document with id: {}", insertedId);
        } else {
            // Update existing document
            DynamicDocument doc = existing.get(0);
            Map<String, Object> dynamicFields = doc.getDynamicFields();
            if (dynamicFields == null) {
                dynamicFields = new HashMap<>();
            }
            dynamicFields.putAll(document);
            doc.setDynamicFields(dynamicFields);
            doc.setLastModifiedAt(Instant.now());
            doc.setVersion(doc.getVersion() != null ? doc.getVersion() + 1 : 1L);

            updateDocument(doc);

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
        StringBuilder selectSql = new StringBuilder("SELECT * FROM dynamic_documents WHERE table_name = :tableName AND is_deleted = false");
        if (whereClause != null && !whereClause.isEmpty()) {
            selectSql.append(" AND ").append(whereClause);
        }
        if (!deleteMultiple) {
            selectSql.append(" LIMIT 1");
        }

        MapSqlParameterSource selectParams = new MapSqlParameterSource();
        selectParams.addValue("tableName", tableName);
        if (params != null) {
            params.forEach(selectParams::addValue);
        }

        List<DynamicDocument> documents = jdbcTemplate.query(selectSql.toString(), selectParams, documentRowMapper);

        // Soft delete each document
        Instant now = Instant.now();
        for (DynamicDocument doc : documents) {
            doc.setDeleted(true);
            doc.setLastModifiedAt(now);
            doc.setVersion(doc.getVersion() != null ? doc.getVersion() + 1 : 1L);
            if (requestId != null) {
                doc.setLatestRequestId(requestId);
            }
            updateDocument(doc);
        }

        logger.info("Soft delete result: modified={}", documents.size());
        return documents.size();
    }

    /**
     * Finds documents matching the given criteria (for use with QueryBuilder results)
     */
    public List<DynamicDocument> findDocuments(String tableName, String whereClause,
                                                Map<String, Object> params, Integer limit) {
        StringBuilder sql = new StringBuilder("SELECT * FROM dynamic_documents WHERE table_name = :tableName AND is_deleted = false");
        if (whereClause != null && !whereClause.isEmpty()) {
            sql.append(" AND ").append(whereClause);
        }
        if (limit != null && limit > 0) {
            sql.append(" LIMIT ").append(limit);
        }

        MapSqlParameterSource paramSource = new MapSqlParameterSource();
        paramSource.addValue("tableName", tableName);
        if (params != null) {
            params.forEach(paramSource::addValue);
        }

        return jdbcTemplate.query(sql.toString(), paramSource, documentRowMapper);
    }

    /**
     * Get a single document by ID
     */
    public DynamicDocument findById(String tableName, Long id) {
        return crudRepository.findByIdAndTableName(id, tableName).orElse(null);
    }

    /**
     * Save a document (insert or update)
     */
    @Transactional
    public DynamicDocument save(DynamicDocument document) {
        if (document.getId() == null) {
            Long id = insertOne(document.getTableName(), document);
            document.setId(id);
        } else {
            document.setLastModifiedAt(Instant.now());
            document.setVersion(document.getVersion() != null ? document.getVersion() + 1 : 1L);
            updateDocument(document);
        }
        return document;
    }

    /**
     * Save multiple documents
     */
    @Transactional
    public List<DynamicDocument> saveAll(List<DynamicDocument> documents) {
        for (DynamicDocument doc : documents) {
            save(doc);
        }
        return documents;
    }

    private MapSqlParameterSource buildInsertParams(DynamicDocument document) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("tableName", document.getTableName());
        params.addValue("data", toJsonString(document.getDynamicFields()));
        params.addValue("version", document.getVersion());
        params.addValue("isDeleted", document.isDeleted());
        params.addValue("latestRequestId", document.getLatestRequestId());
        params.addValue("createdBy", document.getCreatedBy());
        params.addValue("lastModifiedBy", document.getLastModifiedBy());
        params.addValue("createdAt", document.getCreatedAt() != null ? Timestamp.from(document.getCreatedAt()) : null);
        params.addValue("lastModifiedAt", document.getLastModifiedAt() != null ? Timestamp.from(document.getLastModifiedAt()) : null);
        return params;
    }

    private String toJsonString(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map != null ? map : new HashMap<>());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to convert Map to JSON string", e);
        }
    }
}
