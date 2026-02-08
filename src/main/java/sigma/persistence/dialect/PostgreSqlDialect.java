package sigma.persistence.dialect;

import java.util.List;

/**
 * PostgreSQL dialect implementation using JSONB for JSON document storage.
 * Uses native PostgreSQL JSON operators (->, ->>, ?, @>) for optimal performance.
 */
public class PostgreSqlDialect implements DatabaseDialect {

    @Override
    public DatabaseType getType() {
        return DatabaseType.POSTGRESQL;
    }

    @Override
    public String getCreateTableSql() {
        return """
            CREATE TABLE IF NOT EXISTS dynamic_documents (
                id BIGSERIAL PRIMARY KEY,
                table_name VARCHAR(255) NOT NULL,
                data JSONB DEFAULT '{}',
                version BIGINT DEFAULT 0,
                is_deleted BOOLEAN DEFAULT FALSE,
                latest_request_id VARCHAR(255),
                created_by VARCHAR(255),
                last_modified_by VARCHAR(255),
                created_at TIMESTAMP WITH TIME ZONE,
                last_modified_at TIMESTAMP WITH TIME ZONE,
                sequence_number BIGINT NOT NULL DEFAULT 0
            )
            """;
    }

    @Override
    public List<String> getCreateIndexesSql() {
        return List.of(
            "CREATE INDEX IF NOT EXISTS idx_dynamic_documents_table_name ON dynamic_documents(table_name)",
            "CREATE INDEX IF NOT EXISTS idx_dynamic_documents_table_name_not_deleted ON dynamic_documents(table_name, is_deleted)",
            "CREATE INDEX IF NOT EXISTS idx_dynamic_documents_last_modified ON dynamic_documents(table_name, last_modified_at)",
            "CREATE INDEX IF NOT EXISTS idx_dynamic_documents_sequence ON dynamic_documents(table_name, sequence_number)",
            "CREATE INDEX IF NOT EXISTS idx_dynamic_documents_data ON dynamic_documents USING GIN (data)"
        );
    }

    @Override
    public List<String> getSequenceSupportSql() {
        return List.of(
            "CREATE SEQUENCE IF NOT EXISTS dynamic_documents_seq_num START WITH 1 INCREMENT BY 1",
            """
                CREATE OR REPLACE FUNCTION update_sequence_number()
                RETURNS TRIGGER AS $$
                BEGIN
                    NEW.sequence_number := nextval('dynamic_documents_seq_num');
                    RETURN NEW;
                END;
                $$ LANGUAGE plpgsql
                """,
            "DROP TRIGGER IF EXISTS trg_update_sequence_number ON dynamic_documents",
            """
                CREATE TRIGGER trg_update_sequence_number
                    BEFORE INSERT OR UPDATE ON dynamic_documents
                    FOR EACH ROW
                    EXECUTE FUNCTION update_sequence_number()
                """
        );
    }

    @Override
    public String jsonExtractText(String column, String fieldPath) {
        return String.format("%s->>'%s'", column, escapeFieldPath(fieldPath));
    }

    @Override
    public String jsonExtractJson(String column, String fieldPath) {
        return String.format("%s->'%s'", column, escapeFieldPath(fieldPath));
    }

    @Override
    public String jsonNumericValue(String column, String fieldPath) {
        return String.format("CAST(%s->>'%s' AS DOUBLE PRECISION)", column, escapeFieldPath(fieldPath));
    }

    @Override
    public String jsonEquals(String column, String fieldPath, String paramName) {
        return String.format("%s->>'%s' = :%s", column, escapeFieldPath(fieldPath), paramName);
    }

    @Override
    public String jsonNotEquals(String column, String fieldPath, String paramName) {
        String escaped = escapeFieldPath(fieldPath);
        return String.format("(%s->>'%s' IS NULL OR %s->>'%s' != :%s)",
            column, escaped, column, escaped, paramName);
    }

    @Override
    public String jsonGreaterThan(String column, String fieldPath, String paramName) {
        return String.format("CAST(%s->>'%s' AS DOUBLE PRECISION) > :%s",
            column, escapeFieldPath(fieldPath), paramName);
    }

    @Override
    public String jsonGreaterThanOrEqual(String column, String fieldPath, String paramName) {
        return String.format("CAST(%s->>'%s' AS DOUBLE PRECISION) >= :%s",
            column, escapeFieldPath(fieldPath), paramName);
    }

    @Override
    public String jsonLessThan(String column, String fieldPath, String paramName) {
        return String.format("CAST(%s->>'%s' AS DOUBLE PRECISION) < :%s",
            column, escapeFieldPath(fieldPath), paramName);
    }

    @Override
    public String jsonLessThanOrEqual(String column, String fieldPath, String paramName) {
        return String.format("CAST(%s->>'%s' AS DOUBLE PRECISION) <= :%s",
            column, escapeFieldPath(fieldPath), paramName);
    }

    @Override
    public String jsonIn(String column, String fieldPath, String paramName) {
        return String.format("%s->>'%s' IN (:%s)", column, escapeFieldPath(fieldPath), paramName);
    }

    @Override
    public String jsonNotIn(String column, String fieldPath, String paramName) {
        String escaped = escapeFieldPath(fieldPath);
        return String.format("(%s->>'%s' IS NULL OR %s->>'%s' NOT IN (:%s))",
            column, escaped, column, escaped, paramName);
    }

    @Override
    public String jsonLike(String column, String fieldPath, String paramName) {
        return String.format("%s->>'%s' LIKE :%s", column, escapeFieldPath(fieldPath), paramName);
    }

    @Override
    public String jsonExists(String column, String fieldPath, boolean shouldExist) {
        if (shouldExist) {
            return String.format("%s ? '%s'", column, escapeFieldPath(fieldPath));
        } else {
            return String.format("NOT (%s ? '%s')", column, escapeFieldPath(fieldPath));
        }
    }

    @Override
    public String jsonTypeCheck(String column, String fieldPath, String jsonType) {
        return String.format("jsonb_typeof(%s->'%s') = '%s'",
            column, escapeFieldPath(fieldPath), jsonType);
    }

    @Override
    public String jsonArrayExpand(String column, String fieldPath, String alias) {
        return String.format("jsonb_array_elements(%s->'%s') AS %s(value)",
            column, escapeFieldPath(fieldPath), alias);
    }

    @Override
    public String jsonCast(String paramName) {
        return ":" + paramName + "::jsonb";
    }

    @Override
    public String getInsertSql() {
        return """
            INSERT INTO dynamic_documents (table_name, data, version, is_deleted, latest_request_id,
                created_by, last_modified_by, created_at, last_modified_at)
            VALUES (:tableName, :data::jsonb, :version, :isDeleted, :latestRequestId,
                :createdBy, :lastModifiedBy, :createdAt, :lastModifiedAt)
            RETURNING id
            """;
    }

    @Override
    public String getUpdateSql() {
        return """
            UPDATE dynamic_documents
            SET data = :data::jsonb, version = :version, is_deleted = :isDeleted,
                latest_request_id = :latestRequestId, last_modified_by = :lastModifiedBy,
                last_modified_at = :lastModifiedAt
            WHERE id = :id
            """;
    }

    @Override
    public boolean supportsReturningClause() {
        return true;
    }

    @Override
    public String getLastInsertIdSql() {
        return "SELECT currval('dynamic_documents_id_seq')";
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
        return fieldPath.replace("'", "''").replace("\\", "\\\\");
    }
}
