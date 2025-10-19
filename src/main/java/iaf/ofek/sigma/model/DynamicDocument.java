package iaf.ofek.sigma.model;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.HashMap;
import java.util.Map;

/**
 * Concrete class used by the Generic DAL for dynamic, schemaless documents.
 *
 * Features:
 * - Extends AuditableBaseDocument to ensure metadata fields are included
 * - Uses a Map to hold schemaless, user-defined fields
 * - Collection name is determined dynamically by MongoTemplate at runtime
 * - Supports optimistic locking via inherited @Version field
 *
 * This class bridges between:
 * - User's dynamic JSON data (stored in dynamicFields Map)
 * - System-managed audit metadata (inherited from AuditableBaseDocument)
 */
@Getter
@Setter
@Document // No collection name - determined dynamically
public class DynamicDocument extends AuditableBaseDocument {

    /**
     * MongoDB document identifier
     * Maps to the MongoDB '_id' field
     */
    @Id
    private String id;

    /**
     * This Map holds all the dynamic, user-defined fields (the schemaless payload)
     * Contains the actual business data submitted by the user
     */
    private Map<String, Object> dynamicFields;

    /**
     * Default constructor
     */
    public DynamicDocument() {
        this.dynamicFields = new HashMap<>();
    }

    /**
     * Constructor for creating a new document with user data
     */
    public DynamicDocument(Map<String, Object> dynamicFields) {
        this.dynamicFields = dynamicFields != null ? new HashMap<>(dynamicFields) : new HashMap<>();
    }

    /**
     * Constructor with id and user data
     */
    public DynamicDocument(String id, Map<String, Object> dynamicFields) {
        this.id = id;
        this.dynamicFields = dynamicFields != null ? new HashMap<>(dynamicFields) : new HashMap<>();
    }

    /**
     * Convenience method to get a field from dynamicFields
     */
    public Object getField(String fieldName) {
        return dynamicFields != null ? dynamicFields.get(fieldName) : null;
    }

    /**
     * Convenience method to set a field in dynamicFields
     */
    public void setField(String fieldName, Object value) {
        if (dynamicFields == null) {
            dynamicFields = new HashMap<>();
        }
        dynamicFields.put(fieldName, value);
    }

    /**
     * Converts this document to a flat Map containing all fields
     * (both dynamic fields and audit metadata)
     */
    public Map<String, Object> toMap() {
        Map<String, Object> result = new HashMap<>();

        // Add dynamic fields
        if (dynamicFields != null) {
            result.putAll(dynamicFields);
        }

        // Add system fields
        if (id != null) result.put("_id", id);
        if (getVersion() != null) result.put("version", getVersion());
        if (getCreatedAt() != null) result.put("createdAt", getCreatedAt());
        if (getLastModifiedAt() != null) result.put("lastModifiedAt", getLastModifiedAt());
        if (getCreatedBy() != null) result.put("createdBy", getCreatedBy());
        if (getLastModifiedBy() != null) result.put("lastModifiedBy", getLastModifiedBy());
        result.put("isDeleted", isDeleted());

        return result;
    }

    /**
     * Creates a DynamicDocument from a flat Map
     */
    public static DynamicDocument fromMap(Map<String, Object> map) {
        if (map == null) {
            return new DynamicDocument();
        }

        DynamicDocument doc = new DynamicDocument();

        // Extract system fields
        if (map.containsKey("_id")) {
            doc.setId(map.get("_id").toString());
        }
        if (map.containsKey("version")) {
            Object version = map.get("version");
            doc.setVersion(version instanceof Number ? ((Number) version).longValue() : null);
        }

        // Copy dynamic fields (exclude system fields)
        Map<String, Object> dynamicFields = new HashMap<>(map);
        dynamicFields.remove("_id");
        dynamicFields.remove("version");
        dynamicFields.remove("createdAt");
        dynamicFields.remove("lastModifiedAt");
        dynamicFields.remove("createdBy");
        dynamicFields.remove("lastModifiedBy");
        dynamicFields.remove("isDeleted");

        doc.setDynamicFields(dynamicFields);

        if (map.containsKey("isDeleted")) {
            Object deleted = map.get("isDeleted");
            if (deleted instanceof Boolean) {
                doc.setDeleted((Boolean) deleted);
            } else if (deleted != null) {
                doc.setDeleted(Boolean.parseBoolean(deleted.toString()));
            }
        }

        return doc;
    }
}
