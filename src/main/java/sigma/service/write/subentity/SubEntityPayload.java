package sigma.service.write.subentity;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents a sanitized view of an incoming sub-entity payload.
 */
record SubEntityPayload(Long id, boolean deleted, Map<String, Object> attributes) {

    static SubEntityPayload forCreate(String fieldName, Map<?, ?> source) {
        Map<String, Object> normalized = normalize(source);
        if (extractBoolean(normalized, "isDelete", "isDeleted")) {
            throw new IllegalArgumentException(
                    "Sub-entity create payload for '" + fieldName + "' cannot mark entries as deleted");
        }
        Long id = extractLong(normalized, "id");
        return new SubEntityPayload(id, false, normalized);
    }

    static SubEntityPayload forModify(String fieldName, Map<?, ?> source) {
        Map<String, Object> normalized = normalize(source);
        boolean isDelete = extractBoolean(normalized, "isDelete", "isDeleted");
        Long id = extractLong(normalized, "id");
        return new SubEntityPayload(id, isDelete, normalized);
    }

    static SubEntityPayload fromExisting(Map<?, ?> source) {
        Map<String, Object> normalized = normalize(source);
        boolean deleted = extractBoolean(normalized, "isDeleted");
        Long id = extractLong(normalized, "id");
        return new SubEntityPayload(id, deleted, normalized);
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
            return Boolean.parseBoolean(removed.toString());
        }
        return false;
    }

    private static Long extractLong(Map<String, Object> map, String key) {
        Object removed = removeKey(map, key);
        if (removed == null) {
            return null;
        }
        if (removed instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(removed.toString());
        } catch (NumberFormatException e) {
            return null;
        }
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
