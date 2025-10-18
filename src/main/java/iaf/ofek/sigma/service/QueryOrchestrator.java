package iaf.ofek.sigma.service;

import iaf.ofek.sigma.dto.request.QueryRequest;
import iaf.ofek.sigma.dto.response.ErrorResponse;
import iaf.ofek.sigma.dto.response.QueryResponse;
import iaf.ofek.sigma.model.Endpoint;
import iaf.ofek.sigma.service.query.QueryService;
import iaf.ofek.sigma.service.validation.RequestValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Main orchestrator for all query operations
 *
 * This is the core business logic layer that coordinates:
 * - Validation
 * - Query execution
 * - Error handling
 *
 * Can be reused by ANY controller type:
 * - REST (RestApiController)
 * - GraphQL (GraphQLController)
 * - gRPC (GrpcService)
 * - WebSocket (WebSocketHandler)
 * - Message Queue consumers
 * - Scheduled jobs
 *
 * Single Responsibility: Orchestrate the query execution flow
 */
@Service
public class QueryOrchestrator {

    private static final Logger logger = LoggerFactory.getLogger(QueryOrchestrator.class);

    private final RequestValidator requestValidator;
    private final QueryService queryService;

    public QueryOrchestrator(RequestValidator requestValidator, QueryService queryService) {
        this.requestValidator = requestValidator;
        this.queryService = queryService;
    }

    /**
     * Executes a query request - the main entry point for all controllers
     *
     * Flow:
     * 1. Validate request against endpoint configuration
     * 2. If invalid, return error response
     * 3. If valid, execute query
     * 4. Return response (success or error)
     *
     * @param request The parsed query request
     * @param endpoint The endpoint configuration
     * @return Query response (success or error)
     */
    public QueryResponse execute(QueryRequest request, Endpoint endpoint) {
        logger.info("Orchestrating {} query for endpoint: {} -> collection: {}",
                request.getType(), endpoint.getName(), endpoint.getDatabaseCollection());

        try {
            // 1. Validate request
            RequestValidator.ValidationResult validation = requestValidator.validate(request, endpoint);

            if (!validation.isValid()) {
                logger.warn("Validation failed for {}: {}", request.getType(), validation.getErrors());
                return new ErrorResponse("Request validation failed", validation.getErrors());
            }

            // 2. Execute query
            QueryResponse response = queryService.execute(request, endpoint.getDatabaseCollection());

            logger.info("Query executed successfully: {} documents/results", getResponseSize(response));
            return response;

        } catch (IllegalArgumentException e) {
            logger.error("Invalid request parameters: {}", e.getMessage());
            return new ErrorResponse("Invalid request: " + e.getMessage());

        } catch (Exception e) {
            logger.error("Error executing query", e);
            return new ErrorResponse("Internal server error: " + e.getMessage());
        }
    }

    /**
     * Helper to get response size for logging
     */
    private String getResponseSize(QueryResponse response) {
        if (response instanceof iaf.ofek.sigma.dto.response.DocumentListResponse doc) {
            return String.valueOf(doc.getCount());
        }
        if (response instanceof iaf.ofek.sigma.dto.response.SequenceResponse seq) {
            return seq.getData().size() + " (sequence)";
        }
        return "N/A";
    }
}
