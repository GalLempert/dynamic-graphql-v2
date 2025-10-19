package iaf.ofek.sigma.service.write;

import org.bson.Document;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Handles sub-entity lifecycle operations (create/update/delete) within parent documents.
 */
class SubEntityProcessor {

    static final String TECHNICAL_ID_FIELD = "myId";
    private static final String DELETE_FLAG_FIELD = "isDeleted";
    private static final String DELETE_OPERATION_FLAG = "isDelete";
    private static final String DELETE_OPERATION_FLAG_LEGACY = "isdelete";

    private final Set<String> subEntityFields;

    SubEntityProcessor(Set<String> subEntityFields) {
        this.subEntityFields = subEntityFields == null ? Set.of() : Set.copyOf(subEntityFields);
    }

    boolean hasSubEntityConfiguration() {
        return !subEntityFields.isEmpty();
    }

    boolean hasSubEntityPayload(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty() || subEntityFields.isEmpty()) {
            return false;
        }
        return subEntityFields.stream()
                .anyMatch(field -> {
                    Object value = payload.get(field);
                    return value instanceof List<?>;
                });
    }

    void prepareForInsert(Map<String, Object> document) {
        if (!hasSubEntityConfiguration() || document == null) {
            return;
        }

        subEntityFields.forEach(field -> {
            Object value = document.get(field);
            if (!(value instanceof List<?> list)) {
                return; // Nothing to process
            }

            List<Map<String, Object>> processed = new ArrayList<>();
            for (Object element : list) {
                Map<String, Object> entity = convertToMap(element, field);
                if (entity.containsKey(DELETE_OPERATION_FLAG) || entity.containsKey(DELETE_OPERATION_FLAG_LEGACY)) {
                    throw new IllegalArgumentException(
                            "Sub-entity '" + field + "' cannot include delete flag during creation");
                }

                ensureTechnicalId(entity);
                entity.put(DELETE_FLAG_FIELD, Boolean.FALSE);
                processed.add(entity);
            }

            document.put(field, processed);
        });
    }

    void mergeForUpdate(Map<String, Object> updates, Document existingDocument) {
        if (!hasSubEntityConfiguration() || updates == null || existingDocument == null) {
            return;
        }

        for (String field : subEntityFields) {
            if (!(updates.get(field) instanceof List<?> incomingList)) {
                continue;
            }

            List<Map<String, Object>> currentEntities = convertExistingList(existingDocument.get(field));
            Map<String, Integer> indexById = buildIndex(currentEntities);
            List<Map<String, Object>> newEntities = new ArrayList<>();

            for (Object element : incomingList) {
                Map<String, Object> incoming = convertToMap(element, field);
                String technicalId = extractTechnicalId(incoming);
                boolean deleteRequested = extractDeleteFlag(incoming);

                incoming.remove(DELETE_OPERATION_FLAG);
                incoming.remove(DELETE_OPERATION_FLAG_LEGACY);

                if (technicalId == null) {
                    if (deleteRequested) {
                        throw new IllegalArgumentException(
                                "Sub-entity '" + field + "' delete request is missing technical identifier");
                    }
                    ensureTechnicalId(incoming);
                    incoming.put(DELETE_FLAG_FIELD, Boolean.FALSE);
                    newEntities.add(incoming);
                    continue;
                }

                Integer index = indexById.get(technicalId);
                if (index == null) {
                    throw new IllegalArgumentException(
                            "Sub-entity '" + field + "' with id '" + technicalId + "' does not exist");
                }

                Map<String, Object> existingEntity = currentEntities.get(index);
                if (Boolean.TRUE.equals(asBoolean(existingEntity.get(DELETE_FLAG_FIELD)))) {
                    throw new IllegalArgumentException(
                            "Sub-entity '" + field + "' with id '" + technicalId + "' is already deleted");
                }

                if (deleteRequested) {
                    existingEntity.put(DELETE_FLAG_FIELD, Boolean.TRUE);
                } else {
                    for (Map.Entry<String, Object> entry : incoming.entrySet()) {
                        if (!TECHNICAL_ID_FIELD.equals(entry.getKey())) {
                            existingEntity.put(entry.getKey(), entry.getValue());
                        }
                    }
                    existingEntity.put(DELETE_FLAG_FIELD, Boolean.FALSE);
                }
            }

            if (!newEntities.isEmpty()) {
                currentEntities.addAll(newEntities);
            }

            updates.put(field, currentEntities);
        }
    }

    private Map<String, Object> convertToMap(Object element, String fieldName) {
        if (element instanceof Map<?, ?> map) {
            Map<String, Object> converted = new LinkedHashMap<>();
            map.forEach((key, value) -> converted.put(String.valueOf(key), value));
            return converted;
        }
        if (element instanceof Document document) {
            Map<String, Object> converted = new LinkedHashMap<>();
            document.forEach((key, value) -> converted.put(String.valueOf(key), value));
            return converted;
        }
        throw new IllegalArgumentException("Sub-entity '" + fieldName + "' must contain JSON objects");
    }

    private List<Map<String, Object>> convertExistingList(Object value) {
        if (!(value instanceof List<?> list)) {
            return new ArrayList<>();
        }
        return list.stream()
                .map(item -> convertToMap(item, "sub-entity"))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private Map<String, Integer> buildIndex(List<Map<String, Object>> entities) {
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < entities.size(); i++) {
            Object id = entities.get(i).get(TECHNICAL_ID_FIELD);
            if (id != null) {
                index.put(String.valueOf(id), i);
            }
        }
        return index;
    }

    private void ensureTechnicalId(Map<String, Object> entity) {
        if (!entity.containsKey(TECHNICAL_ID_FIELD) || entity.get(TECHNICAL_ID_FIELD) == null ||
                entity.get(TECHNICAL_ID_FIELD).toString().isBlank()) {
            entity.put(TECHNICAL_ID_FIELD, UUID.randomUUID().toString());
        } else {
            entity.put(TECHNICAL_ID_FIELD, String.valueOf(entity.get(TECHNICAL_ID_FIELD)));
        }
    }

    private String extractTechnicalId(Map<String, Object> entity) {
        Object value = entity.get(TECHNICAL_ID_FIELD);
        if (value == null) {
            return null;
        }
        String id = value.toString();
        return id.isBlank() ? null : id;
    }

    private boolean extractDeleteFlag(Map<String, Object> entity) {
        Object explicit = entity.get(DELETE_OPERATION_FLAG);
        if (explicit == null) {
            explicit = entity.get(DELETE_OPERATION_FLAG_LEGACY);
        }
        return asBoolean(explicit);
    }

    private Boolean asBoolean(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(value.toString());
    }
}

