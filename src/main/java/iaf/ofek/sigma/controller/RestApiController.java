package iaf.ofek.sigma.controller;

import iaf.ofek.sigma.dto.request.QueryRequest;
import iaf.ofek.sigma.dto.request.WriteRequest;
import iaf.ofek.sigma.dto.response.QueryResponse;
import iaf.ofek.sigma.model.Endpoint;
import iaf.ofek.sigma.service.Orchestrator;
import iaf.ofek.sigma.service.request.RequestParser;
import iaf.ofek.sigma.service.response.ResponseBuilder;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

/**
 * REST API Controller - Ultra-thin HTTP adapter
 *
 * Responsibilities:
 * 1. Receive HTTP requests
 * 2. Parse HTTP → DTO (via RequestParser)
 * 3. Call business logic (via Orchestrator)
 * 4. Format DTO → HTTP (via ResponseBuilder)
 *
 * Does NOT contain business logic - that's in Orchestrator!
 * This allows the same orchestrator to be used by:
 * - GraphQL controllers
 * - gRPC services
 * - WebSocket handlers
 * - Message queue consumers
 */
@Controller
public class RestApiController {

    private static final Logger logger = LoggerFactory.getLogger(RestApiController.class);

    private final RequestParser requestParser;
    private final Orchestrator orchestrator;
    private final ResponseBuilder responseBuilder;

    public RestApiController(RequestParser requestParser,
                            Orchestrator orchestrator,
                            ResponseBuilder responseBuilder) {
        this.requestParser = requestParser;
        this.orchestrator = orchestrator;
        this.responseBuilder = responseBuilder;
    }

    /**
     * Handles all REST API requests - ultra-thin adapter!
     *
     * This method is ONLY responsible for HTTP concerns:
     * - Parse HTTP request → DTO
     * - Call business logic (orchestrator)
     * - Format DTO → HTTP response
     *
     * All business logic is in Orchestrator (reusable!)
     */
    public ResponseEntity<?> handleRestRequest(String method,
                                              String path,
                                              String body,
                                              Endpoint endpoint,
                                              HttpServletRequest request) {
        logger.debug("REST: {} {} -> {}", method, path, endpoint.getName());

        try {
            // Determine if this is a read or write operation
            // POST can be used for both filtered reads and CREATE writes
            // We check if the endpoint allows this method for writes
            if (isWriteOperation(method, endpoint)) {
                return handleWriteRequest(method, body, endpoint, request);
            } else {
                return handleReadRequest(method, body, endpoint, request);
            }

        } catch (IllegalArgumentException e) {
            logger.error("Invalid request: {}", e.getMessage());
            return responseBuilder.buildError("Invalid request: " + e.getMessage());

        } catch (Exception e) {
            logger.error("Error handling REST request", e);
            return responseBuilder.buildError("Internal server error: " + e.getMessage());
        }
    }

    /**
     * Handles READ operations (GET)
     */
    private ResponseEntity<?> handleReadRequest(String method,
                                               String body,
                                               Endpoint endpoint,
                                               HttpServletRequest request) {
        // 1. Parse HTTP request → QueryRequest DTO
        QueryRequest queryRequest = requestParser.parse(method, body, request, endpoint);

        // 2. Call business logic (orchestrator handles validation + execution)
        QueryResponse queryResponse = orchestrator.executeQuery(queryRequest, endpoint);

        // 3. Format QueryResponse DTO → HTTP ResponseEntity
        return responseBuilder.build(queryResponse);
    }

    /**
     * Handles WRITE operations (POST, PUT, PATCH, DELETE)
     */
    private ResponseEntity<?> handleWriteRequest(String method,
                                                String body,
                                                Endpoint endpoint,
                                                HttpServletRequest request) {
        // 1. Parse HTTP request → WriteRequest DTO
        WriteRequest writeRequest = requestParser.parseWrite(method, body, request, endpoint);

        // 2. Call business logic (orchestrator handles validation + execution)
        iaf.ofek.sigma.dto.response.Response writeResponse = orchestrator.executeWrite(writeRequest, endpoint);

        // 3. Format WriteResponse DTO → HTTP ResponseEntity
        return responseBuilder.buildWrite(writeResponse);
    }

    /**
     * Checks if HTTP method is a write operation for this endpoint
     *
     * Important: POST can be used for BOTH:
     * - Filtered reads (complex queries with JSON body)
     * - CREATE writes (inserting new documents)
     *
     * We check if the endpoint explicitly allows this method for writes.
     * If not configured for writes, we treat it as a read operation.
     */
    private boolean isWriteOperation(String method, Endpoint endpoint) {
        // GET is always a read operation
        if ("GET".equalsIgnoreCase(method)) {
            return false;
        }

        // For other methods (POST, PUT, PATCH, DELETE), check if endpoint allows writes
        return endpoint.isWriteMethodAllowed(method);
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok("OK");
    }
}
