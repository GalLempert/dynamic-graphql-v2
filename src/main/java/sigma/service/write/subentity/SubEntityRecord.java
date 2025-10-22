package sigma.service.write.subentity;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents a sub-entity entry that is embedded within a parent document.
 */
class SubEntityRecord {

    private final String myId;
    private boolean deleted;
    private final Map<String, Object> attributes;

    SubEntityRecord(String myId, boolean deleted, Map<String, Object> attributes) {
        this.myId = myId;
        this.deleted = deleted;
        this.attributes = new LinkedHashMap<>(attributes);
    }

    String getMyId() {
        return myId;
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
        document.put("myId", myId);
        document.put("isDeleted", deleted);
        return document;
    }
}
