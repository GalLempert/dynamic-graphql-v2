package sigma.persistence.dialect;

import java.util.List;

/**
 * H2 dialect implementation for local development and testing.
 * H2 supports JSON type and provides compatibility with both PostgreSQL and Oracle syntax.
 * Uses H2's JSON functions for document operations.
 */
public class H2Dialect implements DatabaseDialect {

    @Override
    public DatabaseType getType() {
        return DatabaseType.H2;
    }

    @Override
    public String getCreateTableSql() {
        return """
            CREATE TABLE IF NOT EXISTS dynamic_documents (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                table_name VARCHAR(255) NOT NULL,
                data CLOB DEFAULT '{}',
                version BIGINT DEFAULT 0,
                is_deleted BOOLEAN DEFAULT FALSE,
                latest_request_id VARCHAR(255),
                created_by VARCHAR(255),
                last_modified_by VARCHAR(255),
                created_at TIMESTAMP WITH TIME ZONE,
                last_modified_at TIMESTAMP WITH TIME ZONE,
                sequence_number BIGINT DEFAULT 0 NOT NULL
            )
            """;
    }

    @Override
    public List<String> getCreateIndexesSql() {
        return List.of(
            "CREATE INDEX IF NOT EXISTS idx_dynamic_documents_table_name ON dynamic_documents(table_name)",
            "CREATE INDEX IF NOT EXISTS idx_dynamic_documents_table_name_not_deleted ON dynamic_documents(table_name, is_deleted)",
            "CREATE INDEX IF NOT EXISTS idx_dynamic_documents_last_modified ON dynamic_documents(table_name, last_modified_at)",
            "CREATE INDEX IF NOT EXISTS idx_dynamic_documents_sequence ON dynamic_documents(table_name, sequence_number)"
        );
    }

    @Override
    public List<String> getSequenceSupportSql() {
        return List.of(
            "CREATE SEQUENCE IF NOT EXISTS dynamic_documents_seq_num START WITH 1 INCREMENT BY 1",
            """
                CREATE TRIGGER IF NOT EXISTS trg_update_sequence_number
                BEFORE INSERT ON dynamic_documents
                FOR EACH ROW
                CALL "sigma.persistence.dialect.H2SequenceTrigger"
                """,
            """
                CREATE TRIGGER IF NOT EXISTS trg_update_sequence_number_upd
                BEFORE UPDATE ON dynamic_documents
                FOR EACH ROW
                CALL "sigma.persistence.dialect.H2SequenceTrigger"
                """
        );
    }

    @Override
    public String jsonExtractText(String column, String fieldPath) {
        // H2 uses JSON_VALUE for extracting scalar values
        return String.format("CAST(JSON_VALUE(%s, '$.%s') AS VARCHAR)", column, escapeFieldPath(fieldPath));
    }

    @Override
    public String jsonExtractJson(String column, String fieldPath) {
        // H2 uses JSON_QUERY for extracting JSON objects/arrays
        return String.format("JSON_QUERY(%s, '$.%s')", column, escapeFieldPath(fieldPath));
    }

    @Override
    public String jsonNumericValue(String column, String fieldPath) {
        return String.format("CAST(JSON_VALUE(%s, '$.%s') AS DOUBLE)", column, escapeFieldPath(fieldPath));
    }

    @Override
    public String jsonEquals(String column, String fieldPath, String paramName) {
        return String.format("CAST(JSON_VALUE(%s, '$.%s') AS VARCHAR) = :%s",
            column, escapeFieldPath(fieldPath), paramName);
    }

    @Override
    public String jsonNotEquals(String column, String fieldPath, String paramName) {
        String escaped = escapeFieldPath(fieldPath);
        return String.format("(JSON_VALUE(%s, '$.%s') IS NULL OR CAST(JSON_VALUE(%s, '$.%s') AS VARCHAR) != :%s)",
            column, escaped, column, escaped, paramName);
    }

    @Override
    public String jsonGreaterThan(String column, String fieldPath, String paramName) {
        return String.format("CAST(JSON_VALUE(%s, '$.%s') AS DOUBLE) > :%s",
            column, escapeFieldPath(fieldPath), paramName);
    }

    @Override
    public String jsonGreaterThanOrEqual(String column, String fieldPath, String paramName) {
        return String.format("CAST(JSON_VALUE(%s, '$.%s') AS DOUBLE) >= :%s",
            column, escapeFieldPath(fieldPath), paramName);
    }

    @Override
    public String jsonLessThan(String column, String fieldPath, String paramName) {
        return String.format("CAST(JSON_VALUE(%s, '$.%s') AS DOUBLE) < :%s",
            column, escapeFieldPath(fieldPath), paramName);
    }

    @Override
    public String jsonLessThanOrEqual(String column, String fieldPath, String paramName) {
        return String.format("CAST(JSON_VALUE(%s, '$.%s') AS DOUBLE) <= :%s",
            column, escapeFieldPath(fieldPath), paramName);
    }

    @Override
    public String jsonIn(String column, String fieldPath, String paramName) {
        return String.format("CAST(JSON_VALUE(%s, '$.%s') AS VARCHAR) IN (:%s)",
            column, escapeFieldPath(fieldPath), paramName);
    }

    @Override
    public String jsonNotIn(String column, String fieldPath, String paramName) {
        String escaped = escapeFieldPath(fieldPath);
        return String.format("(JSON_VALUE(%s, '$.%s') IS NULL OR CAST(JSON_VALUE(%s, '$.%s') AS VARCHAR) NOT IN (:%s))",
            column, escaped, column, escaped, paramName);
    }

    @Override
    public String jsonLike(String column, String fieldPath, String paramName) {
        return String.format("CAST(JSON_VALUE(%s, '$.%s') AS VARCHAR) LIKE :%s",
            column, escapeFieldPath(fieldPath), paramName);
    }

    @Override
    public String jsonExists(String column, String fieldPath, boolean shouldExist) {
        if (shouldExist) {
            return String.format("JSON_VALUE(%s, '$.%s') IS NOT NULL", column, escapeFieldPath(fieldPath));
        } else {
            return String.format("JSON_VALUE(%s, '$.%s') IS NULL", column, escapeFieldPath(fieldPath));
        }
    }

    @Override
    public String jsonTypeCheck(String column, String fieldPath, String jsonType) {
        // H2's type checking is limited; we use heuristics
        String escaped = escapeFieldPath(fieldPath);
        return switch (jsonType) {
            case "string" -> String.format(
                "JSON_VALUE(%s, '$.%s') IS NOT NULL AND JSON_QUERY(%s, '$.%s') IS NULL",
                column, escaped, column, escaped);
            case "number" -> String.format(
                "CAST(JSON_VALUE(%s, '$.%s') AS DOUBLE) IS NOT NULL",
                column, escaped);
            case "boolean" -> String.format(
                "CAST(JSON_VALUE(%s, '$.%s') AS VARCHAR) IN ('true', 'false')",
                column, escaped);
            case "array" -> String.format(
                "JSON_QUERY(%s, '$.%s') IS NOT NULL",
                column, escaped);
            case "object" -> String.format(
                "JSON_QUERY(%s, '$.%s') IS NOT NULL",
                column, escaped);
            case "null" -> String.format(
                "JSON_VALUE(%s, '$.%s') IS NULL",
                column, escaped);
            default -> "1=1";
        };
    }

    @Override
    public String jsonArrayExpand(String column, String fieldPath, String alias) {
        // H2 supports UNNEST for arrays, but JSON array expansion is limited
        // We use a subquery approach that works with H2's JSON functions
        return String.format(
            "UNNEST(SELECT ARRAY_AGG(x) FROM JSON_ARRAY_ELEMENTS(JSON_QUERY(%s, '$.%s')) AS x) AS %s(value)",
            column, escapeFieldPath(fieldPath), alias);
    }

    @Override
    public String jsonCast(String paramName) {
        return ":" + paramName;
    }

    @Override
    public String getInsertSql() {
        return """
            INSERT INTO dynamic_documents (table_name, data, version, is_deleted, latest_request_id,
                created_by, last_modified_by, created_at, last_modified_at)
            VALUES (:tableName, :data, :version, :isDeleted, :latestRequestId,
                :createdBy, :lastModifiedBy, :createdAt, :lastModifiedAt)
            """;
    }

    @Override
    public String getUpdateSql() {
        return """
            UPDATE dynamic_documents
            SET data = :data, version = :version, is_deleted = :isDeleted,
                latest_request_id = :latestRequestId, last_modified_by = :lastModifiedBy,
                last_modified_at = :lastModifiedAt
            WHERE id = :id
            """;
    }

    @Override
    public boolean supportsReturningClause() {
        // H2 2.x supports RETURNING but behavior varies; use IDENTITY() for consistency
        return false;
    }

    @Override
    public String getLastInsertIdSql() {
        return "CALL IDENTITY()";
    }

    @Override
    public String limitClause(int limit) {
        return "LIMIT " + limit;
    }

    @Override
    public String offsetClause(int offset) {
        return "OFFSET " + offset;
    }

    @Override
    public String escapeFieldPath(String fieldPath) {
        return fieldPath.replace("'", "''").replace("\"", "\\\"");
    }
}
