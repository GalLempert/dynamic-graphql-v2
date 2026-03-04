package sigma.model;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

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

    private static final Set<String> SYSTEM_FIELDS = Set.of(
            "id", "version", "createdAt", "lastModifiedAt",
            "createdBy", "lastModifiedBy", "latestRequestId", "isDeleted"
    );

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

        // Add system fields
        if (id != null) result.put("id", id);
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
        doc.setId(toLong(map.get("id")));
        doc.setVersion(toLong(map.get("version")));
        doc.setDeleted(toBoolean(map.get("isDeleted")));
        doc.setLatestRequestId(toStringOrNull(map.get("latestRequestId")));

        Map<String, Object> dynamicFields = new HashMap<>(map);
        SYSTEM_FIELDS.forEach(dynamicFields::remove);
        doc.setDynamicFields(dynamicFields);

        return doc;
    }

    private static Long toLong(Object value) {
        if (value instanceof Number number) return number.longValue();
        if (value != null) {
            try { return Long.parseLong(value.toString()); } catch (NumberFormatException ignored) { }
        }
        return null;
    }

    private static boolean toBoolean(Object value) {
        if (value instanceof Boolean b) return b;
        return value != null && Boolean.parseBoolean(value.toString());
    }

    private static String toStringOrNull(Object value) {
        return value != null ? value.toString() : null;
    }
}
