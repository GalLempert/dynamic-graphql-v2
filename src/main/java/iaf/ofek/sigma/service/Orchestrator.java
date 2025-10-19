package iaf.ofek.sigma.service;

import iaf.ofek.sigma.dto.request.QueryRequest;
import iaf.ofek.sigma.dto.request.WriteRequest;
import iaf.ofek.sigma.dto.response.ErrorResponse;
import iaf.ofek.sigma.dto.response.QueryResponse;
import iaf.ofek.sigma.dto.response.WriteResponse;
import iaf.ofek.sigma.model.Endpoint;
import iaf.ofek.sigma.service.enums.EnumResponseTransformer;
import iaf.ofek.sigma.service.query.QueryService;
import iaf.ofek.sigma.service.validation.RequestValidator;
import iaf.ofek.sigma.service.write.WriteService;
import iaf.ofek.sigma.service.write.WriteValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Main orchestrator for all operations (read and write)
 *
 * This is the core business logic layer that coordinates:
 * - Validation (read and write)
 * - Query execution (read)
 * - Write execution (create, update, delete, upsert)
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
 * Single Responsibility: Orchestrate the request execution flow
 */
@Service
public class Orchestrator {

    private static final Logger logger = LoggerFactory.getLogger(Orchestrator.class);

    private final RequestValidator requestValidator;
    private final QueryService queryService;
    private final WriteValidator writeValidator;
    private final WriteService writeService;
    private final EnumResponseTransformer enumResponseTransformer;

    public Orchestrator(RequestValidator requestValidator,
                       QueryService queryService,
                       WriteValidator writeValidator,
                       WriteService writeService,
                       EnumResponseTransformer enumResponseTransformer) {
        this.requestValidator = requestValidator;
        this.queryService = queryService;
        this.writeValidator = writeValidator;
        this.writeService = writeService;
        this.enumResponseTransformer = enumResponseTransformer;
    }

    /**
     * Executes a query (read) request
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
    public QueryResponse executeQuery(QueryRequest request, Endpoint endpoint) {
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
            response = enumResponseTransformer.transform(response, endpoint);

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
     * Executes a write request (create, update, delete, upsert)
     *
     * Flow:
     * 1. Validate request (write method allowed, filter valid, schema valid)
     * 2. If invalid, return error response
     * 3. If valid, execute write operation
     * 4. Return response (success or error)
     *
     * @param request The parsed write request
     * @param endpoint The endpoint configuration
     * @return Response (WriteResponse or ErrorResponse - both implement Response interface)
     */
    public iaf.ofek.sigma.dto.response.Response executeWrite(WriteRequest request, Endpoint endpoint) {
        logger.info("Orchestrating {} write for endpoint: {} -> collection: {}",
                request.getType(), endpoint.getName(), endpoint.getDatabaseCollection());

        try {
            // 1. Validate request
            WriteValidator.ValidationResult validation = writeValidator.validate(request, endpoint);

            if (!validation.isValid()) {
                logger.warn("Validation failed for {}: {}", request.getType(), validation.getErrors());
                return new ErrorResponse("Write validation failed", validation.getErrors());
            }

            // 2. Execute write
            WriteResponse response = writeService.execute(request, endpoint);

            logger.info("Write executed successfully: {} affected", response.getAffectedCount());
            return response;

        } catch (IllegalArgumentException e) {
            logger.error("Invalid write request parameters: {}", e.getMessage());
            return new ErrorResponse("Invalid write request: " + e.getMessage());

        } catch (Exception e) {
            logger.error("Error executing write", e);
            return new ErrorResponse("Internal server error: " + e.getMessage());
        }
    }

    /**
     * Helper to get response size for logging
     * Uses polymorphism - no instanceof checks
     */
    private String getResponseSize(QueryResponse response) {
        return response.getResponseSizeForLogging();
    }
}
