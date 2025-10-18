package iaf.ofek.sigma.dto.request;

/**
 * Base interface for all query request types
 * Represents a request to query data from MongoDB
 */
public interface QueryRequest {

    /**
     * Returns the type of this request
     */
    QueryType getType();

    enum QueryType {
        FULL_COLLECTION,    // Get all documents
        FILTERED,           // Filter-based query
        SEQUENCE_BASED      // Sequence pagination query
    }
}
