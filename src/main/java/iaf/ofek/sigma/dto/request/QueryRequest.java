package iaf.ofek.sigma.dto.request;

import iaf.ofek.sigma.dto.response.QueryResponse;
import org.springframework.data.mongodb.core.query.Query;

/**
 * Base interface for all query request types
 * Represents a request to query data from MongoDB
 * 
 * Uses polymorphism to eliminate switch statements:
 * - Each request type knows how to build its own query
 * - Each request type knows how to validate itself
 * - Each request type knows how to execute itself
 */
public interface QueryRequest {

    /**
     * Returns the type of this request
     */
    QueryType getType();

    /**
     * Template Method: Build MongoDB Query for this request
     * Polymorphic dispatch - no switch needed
     * 
     * @param queryBuilder The query builder to use
     * @return MongoDB Query (may be null for sequence queries)
     */
    Query buildQuery(iaf.ofek.sigma.service.query.QueryBuilder queryBuilder);

    /**
     * Template Method: Validate this request
     * Polymorphic dispatch - no switch needed
     * 
     * @param validator The validator to use
     * @param endpoint The endpoint configuration
     * @return Validation result
     */
    iaf.ofek.sigma.service.validation.RequestValidator.ValidationResult validate(
            iaf.ofek.sigma.service.validation.RequestValidator validator,
            iaf.ofek.sigma.model.Endpoint endpoint
    );

    /**
     * Template Method: Execute this request
     * Polymorphic dispatch - no switch needed
     * 
     * @param service The query service to use
     * @param collectionName The collection to query
     * @return Query response
     */
    QueryResponse execute(
            iaf.ofek.sigma.service.query.QueryService service,
            String collectionName
    );

    enum QueryType {
        FULL_COLLECTION,    // Get all documents
        FILTERED,           // Filter-based query
        SEQUENCE_BASED      // Sequence pagination query
    }
}
