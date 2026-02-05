package sigma.model.filter;

import sigma.persistence.dialect.DatabaseDialect;
import sigma.persistence.dialect.PostgreSqlDialect;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Factory for creating SQL predicates using the configured database dialect.
 * This is a singleton that can be configured at application startup.
 * Operators use this factory to generate dialect-specific SQL.
 */
public final class SqlPredicateFactory {

    private static final AtomicInteger paramCounter = new AtomicInteger(0);
    private static final String DATA_COLUMN = "d.data";

    private static volatile DatabaseDialect dialect = new PostgreSqlDialect();

    private SqlPredicateFactory() {
        // Singleton - no instantiation
    }

    /**
     * Configures the dialect to use for SQL generation.
     * Should be called once at application startup.
     */
    public static void setDialect(DatabaseDialect newDialect) {
        if (newDialect != null) {
            dialect = newDialect;
        }
    }

    /**
     * Gets the current dialect.
     */
    public static DatabaseDialect getDialect() {
        return dialect;
    }

    /**
     * Generates a unique parameter name to avoid collisions
     */
    public static String generateParamName(String base) {
        return base + "_" + paramCounter.incrementAndGet();
    }

    /**
     * Sanitizes a field name to be used as a parameter name
     */
    public static String sanitizeParamName(String fieldName) {
        return fieldName.replaceAll("[^a-zA-Z0-9]", "_");
    }

    /**
     * Creates a predicate for JSON field equals
     */
    public static SqlPredicate jsonEquals(String fieldName, Object value) {
        if ("id".equals(fieldName)) {
            String paramName = generateParamName("id");
            return new SqlPredicate("d.id = :" + paramName, paramName, value);
        }

        String paramName = generateParamName(sanitizeParamName(fieldName));
        String sql = dialect.jsonEquals(DATA_COLUMN, fieldName, paramName);
        return new SqlPredicate(sql, paramName, value != null ? value.toString() : null);
    }

    /**
     * Creates a predicate for JSON field not equals
     */
    public static SqlPredicate jsonNotEquals(String fieldName, Object value) {
        if ("id".equals(fieldName)) {
            String paramName = generateParamName("id");
            return new SqlPredicate("d.id != :" + paramName, paramName, value);
        }

        String paramName = generateParamName(sanitizeParamName(fieldName));
        String sql = dialect.jsonNotEquals(DATA_COLUMN, fieldName, paramName);
        return new SqlPredicate(sql, paramName, value != null ? value.toString() : null);
    }

    /**
     * Creates a predicate for numeric JSON comparison (greater than)
     */
    public static SqlPredicate jsonGreaterThan(String fieldName, Object value) {
        if ("id".equals(fieldName)) {
            String paramName = generateParamName("id");
            return new SqlPredicate("d.id > :" + paramName, paramName, toLong(value));
        }

        String paramName = generateParamName(sanitizeParamName(fieldName));
        String sql = dialect.jsonGreaterThan(DATA_COLUMN, fieldName, paramName);
        return new SqlPredicate(sql, paramName, toDouble(value));
    }

    /**
     * Creates a predicate for numeric JSON comparison (greater than or equal)
     */
    public static SqlPredicate jsonGreaterThanOrEqual(String fieldName, Object value) {
        if ("id".equals(fieldName)) {
            String paramName = generateParamName("id");
            return new SqlPredicate("d.id >= :" + paramName, paramName, toLong(value));
        }

        String paramName = generateParamName(sanitizeParamName(fieldName));
        String sql = dialect.jsonGreaterThanOrEqual(DATA_COLUMN, fieldName, paramName);
        return new SqlPredicate(sql, paramName, toDouble(value));
    }

    /**
     * Creates a predicate for numeric JSON comparison (less than)
     */
    public static SqlPredicate jsonLessThan(String fieldName, Object value) {
        if ("id".equals(fieldName)) {
            String paramName = generateParamName("id");
            return new SqlPredicate("d.id < :" + paramName, paramName, toLong(value));
        }

        String paramName = generateParamName(sanitizeParamName(fieldName));
        String sql = dialect.jsonLessThan(DATA_COLUMN, fieldName, paramName);
        return new SqlPredicate(sql, paramName, toDouble(value));
    }

    /**
     * Creates a predicate for numeric JSON comparison (less than or equal)
     */
    public static SqlPredicate jsonLessThanOrEqual(String fieldName, Object value) {
        if ("id".equals(fieldName)) {
            String paramName = generateParamName("id");
            return new SqlPredicate("d.id <= :" + paramName, paramName, toLong(value));
        }

        String paramName = generateParamName(sanitizeParamName(fieldName));
        String sql = dialect.jsonLessThanOrEqual(DATA_COLUMN, fieldName, paramName);
        return new SqlPredicate(sql, paramName, toDouble(value));
    }

    /**
     * Creates a predicate for JSON IN clause
     */
    public static SqlPredicate jsonIn(String fieldName, List<?> values) {
        if ("id".equals(fieldName)) {
            String paramName = generateParamName("ids");
            return new SqlPredicate("d.id IN (:" + paramName + ")", paramName, values);
        }

        String paramName = generateParamName(sanitizeParamName(fieldName));
        String sql = dialect.jsonIn(DATA_COLUMN, fieldName, paramName);
        List<String> stringValues = values.stream()
                .map(v -> v != null ? v.toString() : null)
                .collect(Collectors.toList());
        return new SqlPredicate(sql, paramName, stringValues);
    }

    /**
     * Creates a predicate for JSON NOT IN clause
     */
    public static SqlPredicate jsonNotIn(String fieldName, List<?> values) {
        if ("id".equals(fieldName)) {
            String paramName = generateParamName("ids");
            return new SqlPredicate("d.id NOT IN (:" + paramName + ")", paramName, values);
        }

        String paramName = generateParamName(sanitizeParamName(fieldName));
        String sql = dialect.jsonNotIn(DATA_COLUMN, fieldName, paramName);
        List<String> stringValues = values.stream()
                .map(v -> v != null ? v.toString() : null)
                .collect(Collectors.toList());
        return new SqlPredicate(sql, paramName, stringValues);
    }

    /**
     * Creates a predicate for JSON regex match (LIKE pattern)
     */
    public static SqlPredicate jsonRegex(String fieldName, Object pattern) {
        String paramName = generateParamName(sanitizeParamName(fieldName));
        String likePattern = convertRegexToLike(pattern.toString());
        String sql = dialect.jsonLike(DATA_COLUMN, fieldName, paramName);
        return new SqlPredicate(sql, paramName, likePattern);
    }

    /**
     * Creates a predicate for JSON field existence check
     */
    public static SqlPredicate jsonExists(String fieldName, boolean shouldExist) {
        String sql = dialect.jsonExists(DATA_COLUMN, fieldName, shouldExist);
        return new SqlPredicate(sql);
    }

    /**
     * Creates a predicate for JSON type check
     */
    public static SqlPredicate jsonType(String fieldName, Object typeValue) {
        String type = typeValue.toString().toLowerCase();
        String jsonType = mapTypeToJsonType(type);
        String sql = dialect.jsonTypeCheck(DATA_COLUMN, fieldName, jsonType);
        return new SqlPredicate(sql);
    }

    private static String mapTypeToJsonType(String type) {
        return switch (type) {
            case "string" -> "string";
            case "number", "int", "long", "double" -> "number";
            case "boolean", "bool" -> "boolean";
            case "array" -> "array";
            case "object" -> "object";
            case "null" -> "null";
            default -> "string";
        };
    }

    private static Double toDouble(Object value) {
        if (value == null) return null;
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return Double.parseDouble(value.toString());
    }

    private static Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return Long.parseLong(value.toString());
    }

    private static String convertRegexToLike(String regex) {
        String like = regex
                .replace("%", "\\%")
                .replace("_", "\\_")
                .replace(".*", "%")
                .replace(".+", "_%")
                .replace(".", "_")
                .replace("^", "")
                .replace("$", "");

        if (!like.startsWith("%")) {
            like = "%" + like;
        }
        if (!like.endsWith("%")) {
            like = like + "%";
        }

        return like;
    }
}
