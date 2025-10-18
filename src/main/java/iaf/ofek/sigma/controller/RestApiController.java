package iaf.ofek.sigma.controller;

import iaf.ofek.sigma.dto.request.QueryRequest;
import iaf.ofek.sigma.dto.response.QueryResponse;
import iaf.ofek.sigma.model.Endpoint;
import iaf.ofek.sigma.service.QueryOrchestrator;
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
 * 3. Call business logic (via QueryOrchestrator)
 * 4. Format DTO → HTTP (via ResponseBuilder)
 *
 * Does NOT contain business logic - that's in QueryOrchestrator!
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
    private final QueryOrchestrator queryOrchestrator;
    private final ResponseBuilder responseBuilder;

    public RestApiController(RequestParser requestParser,
                            QueryOrchestrator queryOrchestrator,
                            ResponseBuilder responseBuilder) {
        this.requestParser = requestParser;
        this.queryOrchestrator = queryOrchestrator;
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
     * All business logic is in QueryOrchestrator (reusable!)
     */
    public ResponseEntity<?> handleRestRequest(String method,
                                              String path,
                                              String body,
                                              Endpoint endpoint,
                                              HttpServletRequest request) {
        logger.debug("REST: {} {} -> {}", method, path, endpoint.getName());

        try {
            // 1. Parse HTTP request → QueryRequest DTO
            QueryRequest queryRequest = requestParser.parse(method, body, request, endpoint);

            // 2. Call business logic (orchestrator handles validation + execution)
            QueryResponse queryResponse = queryOrchestrator.execute(queryRequest, endpoint);

            // 3. Format QueryResponse DTO → HTTP ResponseEntity
            return responseBuilder.build(queryResponse);

        } catch (IllegalArgumentException e) {
            logger.error("Invalid request: {}", e.getMessage());
            return responseBuilder.buildError("Invalid request: " + e.getMessage());

        } catch (Exception e) {
            logger.error("Error handling REST request", e);
            return responseBuilder.buildError("Internal server error: " + e.getMessage());
        }
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok("OK");
    }
}
