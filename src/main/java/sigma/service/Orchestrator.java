package sigma.service;

import sigma.dto.request.QueryRequest;
import sigma.dto.request.WriteRequest;
import sigma.dto.response.ErrorResponse;
import sigma.dto.response.QueryResponse;
import sigma.dto.response.Response;
import sigma.dto.response.WriteResponse;
import sigma.model.Endpoint;
import sigma.service.enums.EnumResponseTransformer;
import sigma.service.query.QueryService;
import sigma.service.validation.RequestValidator;
import sigma.service.validation.ValidationResult;
import sigma.service.write.WriteService;
import sigma.service.write.WriteValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Main orchestrator for all operations (read and write).
 * Coordinates validation, execution, and error handling.
 * Protocol-agnostic: reusable by REST, GraphQL, gRPC, or any other entry point.
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

    public QueryResponse executeQuery(QueryRequest request, Endpoint endpoint) {
        logger.info("Orchestrating {} query for endpoint: {} -> collection: {}",
                request.getType(), endpoint.getName(), endpoint.getDatabaseCollection());

        try {
            ValidationResult validation = requestValidator.validate(request, endpoint);
            if (!validation.isValid()) {
                logger.warn("Validation failed for {}: {}", request.getType(), validation.getErrors());
                return new ErrorResponse("Request validation failed", validation.getErrors());
            }

            QueryResponse response = queryService.execute(request, endpoint);
            response = enumResponseTransformer.transform(response, endpoint);

            logger.info("Query executed successfully: {}", response.getResponseSizeForLogging());
            return response;

        } catch (IllegalArgumentException e) {
            logger.error("Invalid request parameters: {}", e.getMessage());
            return new ErrorResponse("Invalid request: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Error executing query", e);
            return new ErrorResponse("Internal server error: " + e.getMessage());
        }
    }

    public Response executeWrite(WriteRequest request, Endpoint endpoint) {
        logger.info("Orchestrating {} write for endpoint: {} -> collection: {}",
                request.getType(), endpoint.getName(), endpoint.getDatabaseCollection());

        try {
            ValidationResult validation = writeValidator.validate(request, endpoint);
            if (!validation.isValid()) {
                logger.warn("Validation failed for {}: {}", request.getType(), validation.getErrors());
                return new ErrorResponse("Write validation failed", validation.getErrors());
            }

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
}
