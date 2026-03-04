package sigma.model.filter;

import java.util.Map;

/**
 * Shared mapping from user-facing type names to JSON type names.
 * Used by SqlPredicateFactory, SqlPredicateBuilder, and SqlPredicate.
 */
public final class JsonTypeMapping {

    private JsonTypeMapping() {
    }

    private static final Map<String, String> TYPE_MAP = Map.ofEntries(
            Map.entry("string", "string"),
            Map.entry("number", "number"),
            Map.entry("int", "number"),
            Map.entry("long", "number"),
            Map.entry("double", "number"),
            Map.entry("boolean", "boolean"),
            Map.entry("bool", "boolean"),
            Map.entry("array", "array"),
            Map.entry("object", "object"),
            Map.entry("null", "null")
    );

    /**
     * Maps a user-provided type string to the canonical JSON type.
     * Returns "string" for unknown types.
     */
    public static String toJsonType(String userType) {
        return TYPE_MAP.getOrDefault(userType.toLowerCase(), "string");
    }
}
