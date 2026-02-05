package sigma.model;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.util.HashMap;
import java.util.Map;

/**
 * Concrete class used by the Generic DAL for dynamic, schemaless entities.
 *
 * Features:
 * - Extends AuditableBaseDocument to ensure metadata fields are included
 * - Uses a JSONB column to hold schemaless, user-defined fields
 * - Table name stored in a column to support multiple logical collections in one table
 * - Supports optimistic locking via inherited @Version field
 * - Uses sequence-based Long IDs instead of UUIDs
 *
 * This class bridges between:
 * - User's dynamic JSON data (stored in dynamicFields JSONB column)
 * - System-managed audit metadata (inherited from AuditableBaseDocument)
 */
@Getter
@Setter
@Table("dynamic_documents")
public class DynamicDocument extends AuditableBaseDocument {

    /**
     * PostgreSQL sequence-based identifier
     * Auto-generated using a sequence
     */
    @Id
    private Long id;

    /**
     * Logical table/collection name
     * Allows multiple logical collections to coexist in the same physical table
     */
    @Column("table_name")
    private String tableName;

    /**
     * This JSONB column holds all the dynamic, user-defined fields (the schemaless payload)
     * Contains the actual business data submitted by the user
     */
    @Column("data")
    private Map<String, Object> dynamicFields;

    /**
     * Sequence number for change tracking
     * Automatically updated by a PostgreSQL trigger on insert/update
     */
    @Column("sequence_number")
    private Long sequenceNumber;

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
     * Constructor with table name and user data
     */
    public DynamicDocument(String tableName, Map<String, Object> dynamicFields) {
        this.tableName = tableName;
        this.dynamicFields = dynamicFields != null ? new HashMap<>(dynamicFields) : new HashMap<>();
    }

    /**
     * Constructor with id, table name and user data
     */
    public DynamicDocument(Long id, String tableName, Map<String, Object> dynamicFields) {
        this.id = id;
        this.tableName = tableName;
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

        // Add system fields - use _id for API compatibility
        if (id != null) result.put("_id", id);
        if (getVersion() != null) result.put("version", getVersion());
        if (getCreatedAt() != null) result.put("createdAt", getCreatedAt());
        if (getLastModifiedAt() != null) result.put("lastModifiedAt", getLastModifiedAt());
        if (getCreatedBy() != null) result.put("createdBy", getCreatedBy());
        if (getLastModifiedBy() != null) result.put("lastModifiedBy", getLastModifiedBy());
        if (getLatestRequestId() != null) result.put("latestRequestId", getLatestRequestId());
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
            Object idValue = map.get("_id");
            if (idValue instanceof Number) {
                doc.setId(((Number) idValue).longValue());
            } else if (idValue != null) {
                try {
                    doc.setId(Long.parseLong(idValue.toString()));
                } catch (NumberFormatException e) {
                    // ID is not a valid number, leave it null
                }
            }
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
        dynamicFields.remove("latestRequestId");
        dynamicFields.remove("isDeleted");

        if (map.containsKey("isDeleted")) {
            Object deleted = map.get("isDeleted");
            doc.setDeleted(deleted instanceof Boolean ? (Boolean) deleted : Boolean.parseBoolean(deleted.toString()));
        }

        if (map.containsKey("latestRequestId")) {
            Object requestId = map.get("latestRequestId");
            if (requestId != null) {
                doc.setLatestRequestId(requestId.toString());
            }
        }

        doc.setDynamicFields(dynamicFields);

        return doc;
    }
}
