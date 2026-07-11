package sigma.persistence.dialect;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * JSON path functions for H2, registered as SQL aliases by the H2 dialect.
 *
 * H2 2.x has no built-in JSON_VALUE / JSON_QUERY functions, so the dialect
 * installs these static methods via CREATE ALIAS to provide the same
 * behavior the generated SQL expects.
 *
 * Supported path syntax: {@code $.field} and nested {@code $.a.b.c}.
 */
public final class H2JsonFunctions {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private H2JsonFunctions() {
    }

    /**
     * Extracts a scalar value as text (like SQL/JSON JSON_VALUE).
     * Returns null when the path is missing or points at an object/array.
     */
    public static String jsonValue(String json, String path) {
        JsonNode node = resolve(json, path);
        if (node == null || node.isNull() || node.isContainer()) {
            return null;
        }
        return node.asText();
    }

    /**
     * Extracts an object or array as JSON text (like SQL/JSON JSON_QUERY).
     * Returns null when the path is missing or points at a scalar.
     */
    public static String jsonQuery(String json, String path) {
        JsonNode node = resolve(json, path);
        if (node == null || !node.isContainer()) {
            return null;
        }
        return node.toString();
    }

    /**
     * Extracts a numeric value as Double, or null when the path is missing
     * or the value is not a number. Used for numeric-aware ordering.
     */
    public static Double jsonNumber(String json, String path) {
        JsonNode node = resolve(json, path);
        if (node == null || !node.isNumber()) {
            return null;
        }
        return node.doubleValue();
    }

    private static JsonNode resolve(String json, String path) {
        if (json == null || path == null) {
            return null;
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(json);
            String normalized = path.startsWith("$.") ? path.substring(2)
                    : path.startsWith("$") ? path.substring(1)
                    : path;
            if (normalized.isEmpty()) {
                return node;
            }
            for (String segment : normalized.split("\\.")) {
                if (node == null) {
                    return null;
                }
                node = node.get(segment);
            }
            return node;
        } catch (Exception e) {
            return null;
        }
    }
}
