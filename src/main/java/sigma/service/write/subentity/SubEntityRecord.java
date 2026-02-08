package sigma.service.write.subentity;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents a sub-entity entry that is embedded within a parent document.
 */
class SubEntityRecord {

    private final Long id;
    private boolean deleted;
    private final Map<String, Object> attributes;

    SubEntityRecord(Long id, boolean deleted, Map<String, Object> attributes) {
        this.id = id;
        this.deleted = deleted;
        this.attributes = new LinkedHashMap<>(attributes);
    }

    Long getId() {
        return id;
    }

    boolean isDeleted() {
        return deleted;
    }

    void markDeleted() {
        this.deleted = true;
    }

    void markActive() {
        this.deleted = false;
    }

    void updateAttributes(Map<String, Object> newValues) {
        attributes.putAll(newValues);
    }

    Map<String, Object> toDocument() {
        Map<String, Object> document = new LinkedHashMap<>(attributes);
        document.put("id", id);
        document.put("isDeleted", deleted);
        return document;
    }
}
