package sigma.model.filter;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Represents a SQL predicate with named parameters for PostgreSQL JSONB queries.
 * Used to build WHERE clauses for filtering dynamic documents.
 */
public class SqlPredicate {

    private static final AtomicInteger paramCounter = new AtomicInteger(0);

    private final String sql;
    private final Map<String, Object> parameters;

    public SqlPredicate(String sql) {
        this.sql = sql;
        this.parameters = new HashMap<>();
    }

    public SqlPredicate(String sql, Map<String, Object> parameters) {
        this.sql = sql;
        this.parameters = new HashMap<>(parameters);
    }

    public SqlPredicate(String sql, String paramName, Object paramValue) {
        this.sql = sql;
        this.parameters = new HashMap<>();
        this.parameters.put(paramName, paramValue);
    }

    public String getSql() {
        return sql;
    }

    public Map<String, Object> getParameters() {
        return Collections.unmodifiableMap(parameters);
    }

    /**
     * Generates a unique parameter name to avoid collisions
     */
    public static String generateParamName(String base) {
        return base + "_" + paramCounter.incrementAndGet();
    }

    /**
     * Combines multiple predicates with AND
     */
    public static SqlPredicate and(SqlPredicate... predicates) {
        if (predicates == null || predicates.length == 0) {
            return new SqlPredicate("1=1");
        }
        if (predicates.length == 1) {
            return predicates[0];
        }

        StringBuilder sql = new StringBuilder("(");
        Map<String, Object> allParams = new HashMap<>();

        for (int i = 0; i < predicates.length; i++) {
            if (i > 0) {
                sql.append(" AND ");
            }
            sql.append(predicates[i].getSql());
            allParams.putAll(predicates[i].getParameters());
        }
        sql.append(")");

        return new SqlPredicate(sql.toString(), allParams);
    }

    /**
     * Combines multiple predicates with OR
     */
    public static SqlPredicate or(SqlPredicate... predicates) {
        if (predicates == null || predicates.length == 0) {
            return new SqlPredicate("1=0");
        }
        if (predicates.length == 1) {
            return predicates[0];
        }

        StringBuilder sql = new StringBuilder("(");
        Map<String, Object> allParams = new HashMap<>();

        for (int i = 0; i < predicates.length; i++) {
            if (i > 0) {
                sql.append(" OR ");
            }
            sql.append(predicates[i].getSql());
            allParams.putAll(predicates[i].getParameters());
        }
        sql.append(")");

        return new SqlPredicate(sql.toString(), allParams);
    }

    /**
     * Negates a predicate
     */
    public static SqlPredicate not(SqlPredicate predicate) {
        return new SqlPredicate("NOT (" + predicate.getSql() + ")", predicate.getParameters());
    }

    /**
     * Creates a predicate for JSONB field comparison using text extraction
     * data->>'fieldName' = :param
     */
    public static SqlPredicate jsonbEquals(String fieldName, Object value) {
        // Handle _id field specially - it's a direct column, not in JSONB
        if ("_id".equals(fieldName)) {
            String paramName = generateParamName("id");
            return new SqlPredicate("d.id = :" + paramName, paramName, value);
        }

        String paramName = generateParamName(sanitizeParamName(fieldName));
        String sql = String.format("d.dynamicFields->>'%s' = :%s", escapeJsonKey(fieldName), paramName);
        return new SqlPredicate(sql, paramName, value != null ? value.toString() : null);
    }

    /**
     * Creates a predicate for JSONB field not equals
     */
    public static SqlPredicate jsonbNotEquals(String fieldName, Object value) {
        if ("_id".equals(fieldName)) {
            String paramName = generateParamName("id");
            return new SqlPredicate("d.id != :" + paramName, paramName, value);
        }

        String paramName = generateParamName(sanitizeParamName(fieldName));
        String sql = String.format("(d.dynamicFields->>'%s' IS NULL OR d.dynamicFields->>'%s' != :%s)",
                escapeJsonKey(fieldName), escapeJsonKey(fieldName), paramName);
        return new SqlPredicate(sql, paramName, value != null ? value.toString() : null);
    }

    /**
     * Creates a predicate for numeric JSONB comparison (greater than)
     */
    public static SqlPredicate jsonbGreaterThan(String fieldName, Object value) {
        if ("_id".equals(fieldName)) {
            String paramName = generateParamName("id");
            return new SqlPredicate("d.id > :" + paramName, paramName, toLong(value));
        }

        String paramName = generateParamName(sanitizeParamName(fieldName));
        // Cast to numeric for comparison
        String sql = String.format("CAST(d.dynamicFields->>'%s' AS DOUBLE PRECISION) > :%s",
                escapeJsonKey(fieldName), paramName);
        return new SqlPredicate(sql, paramName, toDouble(value));
    }

    /**
     * Creates a predicate for numeric JSONB comparison (greater than or equal)
     */
    public static SqlPredicate jsonbGreaterThanOrEqual(String fieldName, Object value) {
        if ("_id".equals(fieldName)) {
            String paramName = generateParamName("id");
            return new SqlPredicate("d.id >= :" + paramName, paramName, toLong(value));
        }

        String paramName = generateParamName(sanitizeParamName(fieldName));
        String sql = String.format("CAST(d.dynamicFields->>'%s' AS DOUBLE PRECISION) >= :%s",
                escapeJsonKey(fieldName), paramName);
        return new SqlPredicate(sql, paramName, toDouble(value));
    }

    /**
     * Creates a predicate for numeric JSONB comparison (less than)
     */
    public static SqlPredicate jsonbLessThan(String fieldName, Object value) {
        if ("_id".equals(fieldName)) {
            String paramName = generateParamName("id");
            return new SqlPredicate("d.id < :" + paramName, paramName, toLong(value));
        }

        String paramName = generateParamName(sanitizeParamName(fieldName));
        String sql = String.format("CAST(d.dynamicFields->>'%s' AS DOUBLE PRECISION) < :%s",
                escapeJsonKey(fieldName), paramName);
        return new SqlPredicate(sql, paramName, toDouble(value));
    }

    /**
     * Creates a predicate for numeric JSONB comparison (less than or equal)
     */
    public static SqlPredicate jsonbLessThanOrEqual(String fieldName, Object value) {
        if ("_id".equals(fieldName)) {
            String paramName = generateParamName("id");
            return new SqlPredicate("d.id <= :" + paramName, paramName, toLong(value));
        }

        String paramName = generateParamName(sanitizeParamName(fieldName));
        String sql = String.format("CAST(d.dynamicFields->>'%s' AS DOUBLE PRECISION) <= :%s",
                escapeJsonKey(fieldName), paramName);
        return new SqlPredicate(sql, paramName, toDouble(value));
    }

    /**
     * Creates a predicate for JSONB IN clause
     */
    public static SqlPredicate jsonbIn(String fieldName, java.util.List<?> values) {
        if ("_id".equals(fieldName)) {
            String paramName = generateParamName("ids");
            return new SqlPredicate("d.id IN (:" + paramName + ")", paramName, values);
        }

        String paramName = generateParamName(sanitizeParamName(fieldName));
        String sql = String.format("d.dynamicFields->>'%s' IN (:%s)", escapeJsonKey(fieldName), paramName);
        // Convert all values to strings for text comparison
        java.util.List<String> stringValues = values.stream()
                .map(v -> v != null ? v.toString() : null)
                .collect(java.util.stream.Collectors.toList());
        return new SqlPredicate(sql, paramName, stringValues);
    }

    /**
     * Creates a predicate for JSONB NOT IN clause
     */
    public static SqlPredicate jsonbNotIn(String fieldName, java.util.List<?> values) {
        if ("_id".equals(fieldName)) {
            String paramName = generateParamName("ids");
            return new SqlPredicate("d.id NOT IN (:" + paramName + ")", paramName, values);
        }

        String paramName = generateParamName(sanitizeParamName(fieldName));
        String sql = String.format("(d.dynamicFields->>'%s' IS NULL OR d.dynamicFields->>'%s' NOT IN (:%s))",
                escapeJsonKey(fieldName), escapeJsonKey(fieldName), paramName);
        java.util.List<String> stringValues = values.stream()
                .map(v -> v != null ? v.toString() : null)
                .collect(java.util.stream.Collectors.toList());
        return new SqlPredicate(sql, paramName, stringValues);
    }

    /**
     * Creates a predicate for JSONB regex match (LIKE pattern)
     */
    public static SqlPredicate jsonbRegex(String fieldName, Object pattern) {
        String paramName = generateParamName(sanitizeParamName(fieldName));
        // Convert regex to SQL LIKE pattern (basic conversion)
        String likePattern = convertRegexToLike(pattern.toString());
        String sql = String.format("d.dynamicFields->>'%s' LIKE :%s", escapeJsonKey(fieldName), paramName);
        return new SqlPredicate(sql, paramName, likePattern);
    }

    /**
     * Creates a predicate for JSONB field existence check
     */
    public static SqlPredicate jsonbExists(String fieldName, boolean shouldExist) {
        String sql;
        if (shouldExist) {
            sql = String.format("d.dynamicFields ? '%s'", escapeJsonKey(fieldName));
        } else {
            sql = String.format("NOT (d.dynamicFields ? '%s')", escapeJsonKey(fieldName));
        }
        return new SqlPredicate(sql);
    }

    /**
     * Creates a predicate for JSONB type check
     */
    public static SqlPredicate jsonbType(String fieldName, Object typeValue) {
        String type = typeValue.toString().toLowerCase();
        String sql;

        switch (type) {
            case "string":
                sql = String.format("jsonb_typeof(d.dynamicFields->'%s') = 'string'", escapeJsonKey(fieldName));
                break;
            case "number":
            case "int":
            case "long":
            case "double":
                sql = String.format("jsonb_typeof(d.dynamicFields->'%s') = 'number'", escapeJsonKey(fieldName));
                break;
            case "boolean":
            case "bool":
                sql = String.format("jsonb_typeof(d.dynamicFields->'%s') = 'boolean'", escapeJsonKey(fieldName));
                break;
            case "array":
                sql = String.format("jsonb_typeof(d.dynamicFields->'%s') = 'array'", escapeJsonKey(fieldName));
                break;
            case "object":
                sql = String.format("jsonb_typeof(d.dynamicFields->'%s') = 'object'", escapeJsonKey(fieldName));
                break;
            case "null":
                sql = String.format("jsonb_typeof(d.dynamicFields->'%s') = 'null'", escapeJsonKey(fieldName));
                break;
            default:
                sql = "1=1"; // Unknown type, don't filter
        }

        return new SqlPredicate(sql);
    }

    /**
     * Escapes a JSON key for use in PostgreSQL JSONB path expressions
     */
    private static String escapeJsonKey(String key) {
        return key.replace("'", "''").replace("\\", "\\\\");
    }

    /**
     * Sanitizes a field name to be used as a parameter name
     */
    private static String sanitizeParamName(String fieldName) {
        return fieldName.replaceAll("[^a-zA-Z0-9]", "_");
    }

    /**
     * Converts a value to Double for numeric comparisons
     */
    private static Double toDouble(Object value) {
        if (value == null) return null;
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return Double.parseDouble(value.toString());
    }

    /**
     * Converts a value to Long for ID comparisons
     */
    private static Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return Long.parseLong(value.toString());
    }

    /**
     * Basic conversion of regex pattern to SQL LIKE pattern
     * Note: This is a simplified conversion and doesn't handle all regex features
     */
    private static String convertRegexToLike(String regex) {
        // Basic conversions
        String like = regex
                .replace("%", "\\%")     // Escape existing %
                .replace("_", "\\_")     // Escape existing _
                .replace(".*", "%")      // .* -> %
                .replace(".+", "_%")     // .+ -> _% (at least one char)
                .replace(".", "_")       // . -> _ (single char)
                .replace("^", "")        // Remove start anchor
                .replace("$", "");       // Remove end anchor

        // If doesn't start with %, add % at start for partial match
        if (!like.startsWith("%")) {
            like = "%" + like;
        }
        // If doesn't end with %, add % at end for partial match
        if (!like.endsWith("%")) {
            like = like + "%";
        }

        return like;
    }

    @Override
    public String toString() {
        return "SqlPredicate{sql='" + sql + "', params=" + parameters + "}";
    }
}
