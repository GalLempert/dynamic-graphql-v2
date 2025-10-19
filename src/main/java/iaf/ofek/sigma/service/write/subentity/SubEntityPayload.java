package iaf.ofek.sigma.service.write.subentity;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a sanitized view of an incoming sub-entity payload.
 */
record SubEntityPayload(String myId, boolean deleted, Map<String, Object> attributes) {

    static SubEntityPayload forCreate(String fieldName, Map<?, ?> source) {
        Map<String, Object> normalized = normalize(source);
        if (extractBoolean(normalized, "isDelete", "isDeleted")) {
            throw new IllegalArgumentException(
                    "Sub-entity create payload for '" + fieldName + "' cannot mark entries as deleted");
        }
        String myId = extractString(normalized, "myId");
        return new SubEntityPayload(myId, false, normalized);
    }

    static SubEntityPayload forModify(String fieldName, Map<?, ?> source) {
        Map<String, Object> normalized = normalize(source);
        boolean isDelete = extractBoolean(normalized, "isDelete", "isDeleted");
        String myId = extractString(normalized, "myId");
        return new SubEntityPayload(myId, isDelete, normalized);
    }

    static SubEntityPayload fromExisting(Map<?, ?> source) {
        Map<String, Object> normalized = normalize(source);
        boolean deleted = extractBoolean(normalized, "isDeleted");
        String myId = extractString(normalized, "myId");
        return new SubEntityPayload(myId, deleted, normalized);
    }

    private static Map<String, Object> normalize(Map<?, ?> source) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        if (source == null) {
            return normalized;
        }
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            Object key = entry.getKey();
            if (key == null) {
                continue;
            }
            normalized.put(key.toString(), entry.getValue());
        }
        return normalized;
    }

    private static boolean extractBoolean(Map<String, Object> map, String... candidateKeys) {
        for (String key : candidateKeys) {
            Object removed = removeKey(map, key);
            if (removed == null) {
                continue;
            }
            if (removed instanceof Boolean bool) {
                return bool;
            }
            if (removed instanceof Number number) {
                return number.intValue() != 0;
            }
            return Boolean.parseBoolean(Objects.toString(removed));
        }
        return false;
    }

    private static String extractString(Map<String, Object> map, String key) {
        Object removed = removeKey(map, key);
        return removed != null ? Objects.toString(removed) : null;
    }

    private static Object removeKey(Map<String, Object> map, String key) {
        for (String existingKey : map.keySet().toArray(new String[0])) {
            if (existingKey.equalsIgnoreCase(key)) {
                return map.remove(existingKey);
            }
        }
        return null;
    }
}
