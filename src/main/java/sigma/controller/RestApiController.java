package sigma.controller;

import sigma.dto.request.QueryRequest;
import sigma.dto.request.WriteRequest;
import sigma.dto.response.QueryResponse;
import sigma.model.Endpoint;
import sigma.service.Orchestrator;
import sigma.service.request.RequestParser;
import sigma.service.response.ResponseBuilder;
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
            // We inspect the body structure for POST to distinguish read from write
            if (isWriteOperation(method, body, endpoint)) {
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
        sigma.dto.response.Response writeResponse = orchestrator.executeWrite(writeRequest, endpoint);

        // 3. Format WriteResponse DTO → HTTP ResponseEntity
        return responseBuilder.buildWrite(writeResponse);
    }

    /**
     * Checks if HTTP method is a write operation for this endpoint
     *
     * Important: POST can be used for BOTH:
     * - Filtered reads (complex queries with JSON body containing only "filter" and "options")
     * - CREATE writes (inserting new documents)
     *
     * We check:
     * 1. If endpoint allows writes for this method
     * 2. For POST specifically, we inspect the body structure to distinguish read from write
     */
    private boolean isWriteOperation(String method, String body, Endpoint endpoint) {
        // GET is always a read operation
        if ("GET".equalsIgnoreCase(method)) {
            return false;
        }

        // Check if endpoint allows writes for this method
        if (!endpoint.isWriteMethodAllowed(method)) {
            return false;
        }

        // Special case: POST can be read or write depending on body structure
        // POST with only {"filter": ..., "options": ...} = READ (filtered query)
        // POST with document data = WRITE (CREATE)
        if ("POST".equalsIgnoreCase(method) && body != null && !body.isEmpty()) {
            return !isFilterOnlyBody(body);
        }

        // Other write methods (PUT, PATCH, DELETE) are always writes
        return true;
    }

    /**
     * Checks if POST body contains only filter/options (READ) vs document data (WRITE)
     */
    private boolean isFilterOnlyBody(String body) {
        try {
            body = body.trim();

            // If body starts with "[", it's an array of documents = WRITE
            if (body.startsWith("[")) {
                return false;
            }

            // Parse as JSON and check top-level keys
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(body);

            // If it has "filter" or "options" as top-level keys, it's a filtered read
            // Otherwise, it's a document to insert
            return root.has("filter") || root.has("options");

        } catch (Exception e) {
            // If we can't parse, assume it's a write operation (safer default)
            logger.warn("Could not determine if POST body is filter or document: {}", e.getMessage());
            return false;
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
