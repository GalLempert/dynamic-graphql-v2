package sigma.dto.request;

import sigma.dto.response.QueryResponse;
import sigma.model.filter.FilterResult;

/**
 * Base interface for all query request types
 * Represents a request to query data from PostgreSQL
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
     * Template Method: Build FilterResult for this request
     * Polymorphic dispatch - no switch needed
     *
     * @param queryBuilder The query builder to use
     * @return FilterResult (may be null for sequence queries)
     */
    FilterResult buildQuery(sigma.service.query.QueryBuilder queryBuilder);

    /**
     * Template Method: Validate this request
     * Polymorphic dispatch - no switch needed
     *
     * @param validator The validator to use
     * @param endpoint The endpoint configuration
     * @return Validation result
     */
    sigma.service.validation.RequestValidator.ValidationResult validate(
            sigma.service.validation.RequestValidator validator,
            sigma.model.Endpoint endpoint
    );

    /**
     * Template Method: Execute this request
     * Polymorphic dispatch - no switch needed
     *
     * @param service The query service to use
     * @param collectionName The table/collection to query
     * @return Query response
     */
    QueryResponse execute(
            sigma.service.query.QueryService service,
            String collectionName
    );

    enum QueryType {
        FULL_COLLECTION,    // Get all documents
        FILTERED,           // Filter-based query
        SEQUENCE_BASED      // Sequence pagination query
    }
}
