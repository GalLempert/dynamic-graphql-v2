package sigma.persistence.dialect;

import java.util.List;
import java.util.Map;

/**
 * Database dialect abstraction for supporting multiple database types.
 * Each dialect provides database-specific SQL generation for JSON/document operations.
 */
public interface DatabaseDialect {

    /**
     * Returns the dialect type identifier
     */
    DatabaseType getType();

    /**
     * Returns the SQL for creating the dynamic_documents table
     */
    String getCreateTableSql();

    /**
     * Returns the SQL for creating indexes
     */
    List<String> getCreateIndexesSql();

    /**
     * Returns the SQL for creating sequences and triggers for sequence_number
     */
    List<String> getSequenceSupportSql();

    // ===== JSON Field Access =====

    /**
     * Returns SQL expression for extracting a JSON field as text
     * e.g., PostgreSQL: data->>'fieldName', Oracle: JSON_VALUE(data, '$.fieldName')
     */
    String jsonExtractText(String column, String fieldPath);

    /**
     * Returns SQL expression for extracting a JSON field as JSON (preserving type)
     * e.g., PostgreSQL: data->'fieldName', Oracle: JSON_QUERY(data, '$.fieldName')
     */
    String jsonExtractJson(String column, String fieldPath);

    /**
     * Returns SQL expression for numeric comparison on JSON field
     */
    String jsonNumericValue(String column, String fieldPath);

    // ===== JSON Predicates =====

    /**
     * Returns SQL predicate for JSON field equals
     */
    String jsonEquals(String column, String fieldPath, String paramName);

    /**
     * Returns SQL predicate for JSON field not equals
     */
    String jsonNotEquals(String column, String fieldPath, String paramName);

    /**
     * Returns SQL predicate for JSON field greater than
     */
    String jsonGreaterThan(String column, String fieldPath, String paramName);

    /**
     * Returns SQL predicate for JSON field greater than or equal
     */
    String jsonGreaterThanOrEqual(String column, String fieldPath, String paramName);

    /**
     * Returns SQL predicate for JSON field less than
     */
    String jsonLessThan(String column, String fieldPath, String paramName);

    /**
     * Returns SQL predicate for JSON field less than or equal
     */
    String jsonLessThanOrEqual(String column, String fieldPath, String paramName);

    /**
     * Returns SQL predicate for JSON field IN list
     */
    String jsonIn(String column, String fieldPath, String paramName);

    /**
     * Returns SQL predicate for JSON field NOT IN list
     */
    String jsonNotIn(String column, String fieldPath, String paramName);

    /**
     * Returns SQL predicate for JSON field LIKE/regex
     */
    String jsonLike(String column, String fieldPath, String paramName);

    /**
     * Returns SQL predicate for JSON field exists check
     */
    String jsonExists(String column, String fieldPath, boolean shouldExist);

    /**
     * Returns SQL predicate for JSON type check
     */
    String jsonTypeCheck(String column, String fieldPath, String jsonType);

    // ===== Array Operations =====

    /**
     * Returns SQL for expanding JSON array elements (for nested document queries)
     * e.g., PostgreSQL: jsonb_array_elements(data->'field') AS nested(value)
     */
    String jsonArrayExpand(String column, String fieldPath, String alias);

    // ===== Insert/Update Operations =====

    /**
     * Returns the SQL cast expression for inserting JSON data
     * e.g., PostgreSQL: :data::jsonb, Oracle: :data
     */
    String jsonCast(String paramName);

    /**
     * Returns INSERT SQL with returning ID clause
     */
    String getInsertSql();

    /**
     * Returns UPDATE SQL
     */
    String getUpdateSql();

    /**
     * Whether this dialect supports RETURNING clause for INSERT
     */
    boolean supportsReturningClause();

    /**
     * Returns the SQL for getting the last inserted ID (if RETURNING not supported)
     */
    String getLastInsertIdSql();

    // ===== Pagination =====

    /**
     * Returns LIMIT clause syntax
     * e.g., PostgreSQL/H2: LIMIT n, Oracle: FETCH FIRST n ROWS ONLY
     */
    String limitClause(int limit);

    /**
     * Returns OFFSET clause syntax
     * e.g., PostgreSQL/H2: OFFSET n, Oracle: OFFSET n ROWS
     */
    String offsetClause(int offset);

    /**
     * Returns combined pagination clause
     */
    default String paginationClause(Integer limit, Integer offset) {
        StringBuilder sb = new StringBuilder();
        if (limit != null && limit > 0) {
            sb.append(" ").append(limitClause(limit));
        }
        if (offset != null && offset > 0) {
            sb.append(" ").append(offsetClause(offset));
        }
        return sb.toString();
    }

    // ===== Utility =====

    /**
     * Escapes a JSON field path for safe use in SQL
     */
    String escapeFieldPath(String fieldPath);

    /**
     * Returns true if this dialect requires special handling for boolean values in JSON
     */
    default boolean requiresBooleanConversion() {
        return false;
    }

    /**
     * Converts a Java boolean to the database-specific representation
     */
    default Object convertBoolean(boolean value) {
        return value;
    }
}
