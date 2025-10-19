package iaf.ofek.sigma.dto.request;

import java.util.List;
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

    /**
     * Returns documents to validate against schema (if applicable)
     * Returns null for operations that don't require full document schema validation
     *
     * Enables polymorphic validation without instanceof checks (OOP principle)
     */
    default List<Map<String, Object>> getDocumentsForValidation() {
        return null;
    }

    /**
     * Template Method: Execute this write request
     * Polymorphic dispatch - no switch needed
     * 
     * @param service The write service to use
     * @param collectionName The collection to write to
     * @return Write response
     */
    iaf.ofek.sigma.dto.response.WriteResponse execute(
            iaf.ofek.sigma.service.write.WriteService service,
            String collectionName,
            iaf.ofek.sigma.model.Endpoint endpoint
    );

    /**
     * Template Method: Get HTTP method for this write type
     * Polymorphic dispatch - no switch needed
     * 
     * @return HTTP method (POST, PUT, PATCH, DELETE)
     */
    String getHttpMethod();

    enum WriteType {
        CREATE,     // Insert new document(s)
        UPDATE,     // Update existing document(s) matching filter
        DELETE,     // Delete document(s) matching filter
        UPSERT      // Update if exists, insert if not
    }
}
