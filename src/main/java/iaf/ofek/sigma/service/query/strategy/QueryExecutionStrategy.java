package iaf.ofek.sigma.service.query.strategy;

import iaf.ofek.sigma.dto.request.QueryRequest;
import iaf.ofek.sigma.dto.response.QueryResponse;
import iaf.ofek.sigma.model.Endpoint;
import iaf.ofek.sigma.service.query.QueryService;

/**
 * Strategy interface for executing read queries against MongoDB.
 *
 * <p>Allows selecting different execution paths based on endpoint metadata
 * (for example, nested document collections).</p>
 */
public interface QueryExecutionStrategy {

    /**
     * Determines if this strategy supports the provided endpoint.
     */
    boolean supports(Endpoint endpoint);

    /**
     * Executes the query request according to the strategy rules.
     */
    QueryResponse execute(QueryRequest request, Endpoint endpoint, QueryService service);
}

