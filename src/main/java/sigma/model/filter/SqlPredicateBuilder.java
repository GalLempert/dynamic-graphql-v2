package sigma.model.filter;

import sigma.persistence.dialect.DatabaseDialect;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Builds SQL predicates using the configured database dialect.
 * This class provides dialect-aware SQL generation for filtering operations.
 */
@Component
public class SqlPredicateBuilder {

    private static final AtomicInteger paramCounter = new AtomicInteger(0);
    private static final String DATA_COLUMN = "d.data";

    private final DatabaseDialect dialect;

    public SqlPredicateBuilder(DatabaseDialect dialect) {
        this.dialect = dialect;
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
    public SqlPredicate jsonEquals(String fieldName, Object value) {
        // Handle id field specially - it's a direct column, not in JSON
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
    public SqlPredicate jsonNotEquals(String fieldName, Object value) {
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
    public SqlPredicate jsonGreaterThan(String fieldName, Object value) {
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
    public SqlPredicate jsonGreaterThanOrEqual(String fieldName, Object value) {
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
    public SqlPredicate jsonLessThan(String fieldName, Object value) {
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
    public SqlPredicate jsonLessThanOrEqual(String fieldName, Object value) {
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
    public SqlPredicate jsonIn(String fieldName, List<?> values) {
        if ("id".equals(fieldName)) {
            String paramName = generateParamName("ids");
            return new SqlPredicate("d.id IN (:" + paramName + ")", paramName, values);
        }

        String paramName = generateParamName(sanitizeParamName(fieldName));
        String sql = dialect.jsonIn(DATA_COLUMN, fieldName, paramName);
        // Convert all values to strings for text comparison
        List<String> stringValues = values.stream()
                .map(v -> v != null ? v.toString() : null)
                .collect(Collectors.toList());
        return new SqlPredicate(sql, paramName, stringValues);
    }

    /**
     * Creates a predicate for JSON NOT IN clause
     */
    public SqlPredicate jsonNotIn(String fieldName, List<?> values) {
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
    public SqlPredicate jsonRegex(String fieldName, Object pattern) {
        String paramName = generateParamName(sanitizeParamName(fieldName));
        String likePattern = convertRegexToLike(pattern.toString());
        String sql = dialect.jsonLike(DATA_COLUMN, fieldName, paramName);
        return new SqlPredicate(sql, paramName, likePattern);
    }

    /**
     * Creates a predicate for JSON field existence check
     */
    public SqlPredicate jsonExists(String fieldName, boolean shouldExist) {
        String sql = dialect.jsonExists(DATA_COLUMN, fieldName, shouldExist);
        return new SqlPredicate(sql);
    }

    /**
     * Creates a predicate for JSON type check
     */
    public SqlPredicate jsonType(String fieldName, Object typeValue) {
        String type = typeValue.toString().toLowerCase();
        String jsonType = mapTypeToJsonType(type);
        String sql = dialect.jsonTypeCheck(DATA_COLUMN, fieldName, jsonType);
        return new SqlPredicate(sql);
    }

    /**
     * Gets the underlying dialect
     */
    public DatabaseDialect getDialect() {
        return dialect;
    }

    private String mapTypeToJsonType(String type) {
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
