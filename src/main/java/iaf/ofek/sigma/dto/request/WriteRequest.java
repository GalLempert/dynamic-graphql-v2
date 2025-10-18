package iaf.ofek.sigma.dto.request;

import java.util.Map;

/**
 * Base interface for all write request types
 * Represents a request to modify data in MongoDB (Create, Update, Delete, Upsert)
 */
public interface WriteRequest {

    /**
     * Returns the type of this write operation
     */
    WriteType getType();

    /**
     * Returns the request ID for audit trail
     */
    String getRequestId();

    /**
     * Returns the optional filter for targeting specific documents
     * Used in UPDATE and DELETE operations to specify which documents to modify
     */
    Map<String, Object> getFilter();

    enum WriteType {
        CREATE,     // Insert new document(s)
        UPDATE,     // Update existing document(s) matching filter
        DELETE,     // Delete document(s) matching filter
        UPSERT      // Update if exists, insert if not
    }
}
