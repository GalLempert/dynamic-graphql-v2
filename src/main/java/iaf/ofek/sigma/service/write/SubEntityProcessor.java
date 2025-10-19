package iaf.ofek.sigma.service.write;

import org.bson.Document;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Helper component that encapsulates all sub-entity processing logic.
 *
 * Responsibilities:
 * <ul>
 *     <li>Generating technical identifiers (myId) for new sub-entities</li>
 *     <li>Applying update/delete operations on existing sub-entities</li>
 *     <li>Validating that referenced sub-entities exist and are not deleted</li>
 *     <li>Ensuring logical deletes by marking sub-entities with {@code isDeleted=true}</li>
 * </ul>
 */
@Component
public class SubEntityProcessor {

    private static final String MY_ID_FIELD = "myId";
    private static final String IS_DELETED_FIELD = "isDeleted";
    private static final String DELETE_FLAG_FIELD = "isdelete";

    /**
     * Normalizes sub-entity lists during document creation.
     * Ensures each sub-entity has a technical identifier and is marked as active.
     */
    public void prepareForCreate(Map<String, Object> document, Set<String> subEntityFields) {
        if (subEntityFields.isEmpty()) {
            return;
        }

        for (String field : subEntityFields) {
            Object value = document.get(field);
            if (value == null) {
                continue; // Nothing to normalize
            }

            List<Map<String, Object>> subEntities = toListOfMaps(value, field);
            Set<String> usedIds = new HashSet<>();

            List<Map<String, Object>> normalized = new ArrayList<>();
            for (Map<String, Object> subEntity : subEntities) {
                Map<String, Object> copy = new HashMap<>(subEntity);
                copy.remove(IS_DELETED_FIELD);
                normalizeDeleteFlag(copy);

                String myId = copy.containsKey(MY_ID_FIELD)
                        ? asString(copy.get(MY_ID_FIELD), field)
                        : generateMyId();

                if (!usedIds.add(myId)) {
                    throw new IllegalArgumentException(
                            "Duplicate myId '" + myId + "' found in sub-entity list '" + field + "'");
                }

                copy.put(MY_ID_FIELD, myId);
                copy.put(IS_DELETED_FIELD, Boolean.FALSE);
                normalized.add(copy);
            }

            document.put(field, normalized);
        }
    }

    /**
     * Applies create/update/delete operations on an existing sub-entity list.
     *
     * @param existingValue Existing list from the persisted document
     * @param operationsValue User supplied list of operations
     * @param fieldName The sub-entity field name (for error messages)
     * @return A new list reflecting all requested operations
     */
    public List<Map<String, Object>> applyOperations(Object existingValue,
                                                     Object operationsValue,
                                                     String fieldName) {
        List<Map<String, Object>> current = toListOfMaps(existingValue, fieldName);
        List<Map<String, Object>> result = deepCopy(current);
        Map<String, Map<String, Object>> byId = indexById(result, fieldName);

        if (operationsValue == null) {
            return result;
        }

        List<Map<String, Object>> operations = toListOfMaps(operationsValue, fieldName);

        for (Map<String, Object> operation : operations) {
            Map<String, Object> opCopy = new HashMap<>(operation);
            opCopy.remove(IS_DELETED_FIELD);
            boolean deleteRequested = normalizeDeleteFlag(opCopy);
            String myId = opCopy.containsKey(MY_ID_FIELD)
                    ? asString(opCopy.get(MY_ID_FIELD), fieldName)
                    : null;

            if (myId != null) {
                Map<String, Object> target = byId.get(myId);
                if (target == null || Boolean.TRUE.equals(target.get(IS_DELETED_FIELD))) {
                    throw new IllegalArgumentException(
                            "Sub-entity '" + fieldName + "' with myId '" + myId + "' does not exist");
                }

                if (deleteRequested) {
                    target.put(IS_DELETED_FIELD, Boolean.TRUE);
                } else {
                    opCopy.remove(MY_ID_FIELD);
                    target.putAll(opCopy);
                    target.put(MY_ID_FIELD, myId);
                    target.put(IS_DELETED_FIELD, Boolean.FALSE);
                }
            } else {
                if (deleteRequested) {
                    throw new IllegalArgumentException(
                            "Cannot delete sub-entity '" + fieldName + "' without myId");
                }

                String newId = generateMyId();
                opCopy.put(MY_ID_FIELD, newId);
                opCopy.put(IS_DELETED_FIELD, Boolean.FALSE);
                result.add(opCopy);
                byId.put(newId, opCopy);
            }
        }

        return result;
    }

    private List<Map<String, Object>> deepCopy(List<Map<String, Object>> source) {
        List<Map<String, Object>> copy = new ArrayList<>(source.size());
        for (Map<String, Object> entry : source) {
            copy.add(new HashMap<>(entry));
        }
        return copy;
    }

    private Map<String, Map<String, Object>> indexById(List<Map<String, Object>> items, String fieldName) {
        Map<String, Map<String, Object>> index = new HashMap<>();
        for (Map<String, Object> item : items) {
            Object id = item.get(MY_ID_FIELD);
            if (id != null) {
                index.put(asString(id, fieldName), item);
            }
        }
        return index;
    }

    private List<Map<String, Object>> toListOfMaps(Object value, String fieldName) {
        if (value == null) {
            return new ArrayList<>();
        }

        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException(
                    "Field '" + fieldName + "' must be an array of objects representing sub-entities");
        }

        List<Map<String, Object>> result = new ArrayList<>(list.size());
        for (Object element : list) {
            result.add(toMap(element, fieldName));
        }
        return result;
    }

    private Map<String, Object> toMap(Object element, String fieldName) {
        if (element instanceof Document document) {
            return new HashMap<>(document);
        }
        if (element instanceof Map<?, ?> map) {
            Map<String, Object> copy = new HashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Object key = entry.getKey();
                if (!(key instanceof String)) {
                    throw new IllegalArgumentException(
                            "Field '" + fieldName + "' contains a sub-entity with a non-string key");
                }
                copy.put((String) key, entry.getValue());
            }
            return copy;
        }

        throw new IllegalArgumentException(
                "Field '" + fieldName + "' must contain JSON objects representing sub-entities");
    }

    private boolean normalizeDeleteFlag(Map<String, Object> subEntity) {
        boolean deleteRequested = false;
        if (subEntity.containsKey(DELETE_FLAG_FIELD)) {
            deleteRequested = toBoolean(subEntity.remove(DELETE_FLAG_FIELD));
        }
        if (subEntity.containsKey("isDelete")) {
            deleteRequested = deleteRequested || toBoolean(subEntity.remove("isDelete"));
        }
        return deleteRequested;
    }

    private String asString(Object value, String fieldName) {
        if (value == null) {
            return null;
        }
        return String.valueOf(value);
    }

    private boolean toBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String str) {
            return Boolean.parseBoolean(str);
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        return value != null;
    }

    private String generateMyId() {
        return UUID.randomUUID().toString();
    }
}
