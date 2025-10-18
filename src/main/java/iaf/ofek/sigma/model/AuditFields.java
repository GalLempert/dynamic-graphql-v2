package iaf.ofek.sigma.model;

import java.time.Instant;

/**
 * System-managed audit fields that are automatically added to every document
 * These fields are immutable from the client perspective - they cannot be set via API
 *
 * Purpose:
 * - Track document lifecycle (creation, updates)
 * - Audit trail for compliance
 * - Debugging and troubleshooting
 *
 * Fields are ALWAYS present regardless of user permissions or schema
 */
public class AuditFields {

    public static final String CREATED_AT = "_createdAt";
    public static final String UPDATED_AT = "_updatedAt";
    public static final String LAST_REQUEST_ID = "_lastRequestId";
    public static final String PRIMARY_KEY = "_id";

    /**
     * Injects audit fields into a document for CREATE operations
     *
     * @param document The document to inject fields into
     * @param requestId The current request ID
     * @return The document with audit fields added
     */
    public static java.util.Map<String, Object> injectForCreate(
            java.util.Map<String, Object> document,
            String requestId) {

        Instant now = Instant.now();

        // Remove any client-provided audit fields (security)
        removeClientProvidedAuditFields(document);

        // Add system audit fields
        document.put(CREATED_AT, now.toString());
        document.put(UPDATED_AT, now.toString());
        document.put(LAST_REQUEST_ID, requestId);

        return document;
    }

    /**
     * Injects audit fields into a document for UPDATE operations
     *
     * @param document The document to inject fields into
     * @param requestId The current request ID
     * @return The document with audit fields added
     */
    public static java.util.Map<String, Object> injectForUpdate(
            java.util.Map<String, Object> document,
            String requestId) {

        Instant now = Instant.now();

        // Remove any client-provided audit fields (security)
        removeClientProvidedAuditFields(document);

        // Update only mutable audit fields (createdAt should never change)
        document.put(UPDATED_AT, now.toString());
        document.put(LAST_REQUEST_ID, requestId);

        return document;
    }

    /**
     * Removes any audit fields that the client may have tried to set
     * This ensures security - clients cannot manipulate system fields
     */
    private static void removeClientProvidedAuditFields(java.util.Map<String, Object> document) {
        document.remove(CREATED_AT);
        document.remove(UPDATED_AT);
        document.remove(LAST_REQUEST_ID);
        // Note: We don't remove _id as it may be provided for upsert operations
    }

    /**
     * Returns all audit field names
     */
    public static java.util.Set<String> getAllFieldNames() {
        return java.util.Set.of(CREATED_AT, UPDATED_AT, LAST_REQUEST_ID, PRIMARY_KEY);
    }

    /**
     * Checks if a field name is an audit field
     */
    public static boolean isAuditField(String fieldName) {
        return getAllFieldNames().contains(fieldName);
    }
}
