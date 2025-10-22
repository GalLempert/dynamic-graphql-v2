package sigma.service.write.subentity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maintains the state of a sub-entity array during write operations.
 */
class SubEntityCollection {

    private final String fieldName;
    private final Map<String, SubEntityRecord> records;
    private final SubEntityIdGenerator idGenerator;

    private SubEntityCollection(String fieldName,
                                Map<String, SubEntityRecord> records,
                                SubEntityIdGenerator idGenerator) {
        this.fieldName = fieldName;
        this.records = records;
        this.idGenerator = idGenerator;
    }

    static SubEntityCollection empty(String fieldName, SubEntityIdGenerator idGenerator) {
        return new SubEntityCollection(fieldName, new LinkedHashMap<>(), idGenerator);
    }

    static SubEntityCollection fromExisting(String fieldName,
                                            Object currentValue,
                                            SubEntityIdGenerator idGenerator) {
        Map<String, SubEntityRecord> currentRecords = new LinkedHashMap<>();
        SubEntityCollection collection = new SubEntityCollection(fieldName, currentRecords, idGenerator);

        if (currentValue == null) {
            return collection;
        }
        if (!(currentValue instanceof List<?> listValue)) {
            throw new IllegalArgumentException("Existing data for sub-entity field '" + fieldName + "' is not an array");
        }

        for (Object item : listValue) {
            if (!(item instanceof Map<?, ?> itemMap)) {
                throw new IllegalArgumentException(
                        "Existing data for sub-entity field '" + fieldName + "' must be JSON objects");
            }
            collection.addExisting(SubEntityPayload.fromExisting(itemMap));
        }

        return collection;
    }

    void addNew(String requestedId, Map<String, Object> attributes) {
        String identifier = resolveIdentifier(requestedId);
        ensureIdAvailable(identifier);
        SubEntityRecord record = new SubEntityRecord(identifier, false, attributes);
        records.put(identifier, record);
    }

    void addExisting(SubEntityPayload payload) {
        String identifier = payload.myId();
        if (identifier == null || identifier.isBlank() || records.containsKey(identifier)) {
            identifier = resolveIdentifier(null);
        }
        SubEntityRecord record = new SubEntityRecord(identifier, payload.deleted(), payload.attributes());
        records.put(identifier, record);
    }

    void applyCommand(SubEntityCommand command) {
        command.apply(this);
    }

    void update(String id, Map<String, Object> attributes) {
        SubEntityRecord record = getActiveRecord(id);
        record.updateAttributes(attributes);
        record.markActive();
    }

    void delete(String id) {
        SubEntityRecord record = getActiveRecord(id);
        record.markDeleted();
    }

    List<Map<String, Object>> toDocuments() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (SubEntityRecord record : records.values()) {
            result.add(record.toDocument());
        }
        return result;
    }

    private SubEntityRecord getActiveRecord(String id) {
        SubEntityRecord record = records.get(id);
        if (record == null || record.isDeleted()) {
            throw new IllegalArgumentException(
                    "Sub-entity with myId '" + id + "' does not exist or is deleted for field '" + fieldName + "'");
        }
        return record;
    }

    private void ensureIdAvailable(String id) {
        if (records.containsKey(id)) {
            throw new IllegalArgumentException(
                    "Sub-entity with myId '" + id + "' already exists for field '" + fieldName + "'");
        }
    }

    private String resolveIdentifier(String requestedId) {
        String id = requestedId;
        while (id == null || id.isBlank() || records.containsKey(id)) {
            id = idGenerator.generate();
        }
        return id;
    }

}
