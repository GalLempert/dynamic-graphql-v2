package iaf.ofek.sigma.controller;

import iaf.ofek.sigma.engine.GraphQLEngine;
import iaf.ofek.sigma.model.Endpoint;
import jakarta.servlet.http.HttpServletRequest;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API controller
 * Handles traditional REST endpoints with support for sequence-based queries
 */
@Controller
public class RestApiController {

    private static final Logger logger = LoggerFactory.getLogger(RestApiController.class);

    private final GraphQLEngine engine;

    public RestApiController(GraphQLEngine engine) {
        this.engine = engine;
    }

    /**
     * Handles REST API requests
     * Routes to engine layer to query MongoDB collections
     * Supports sequence-based queries via query parameters
     */
    public ResponseEntity<?> handleRestRequest(String method, String path, String body,
                                              Endpoint endpoint, HttpServletRequest request) {
        logger.info("Handling REST request: {} {} -> collection: {}", method, path, endpoint.getDatabaseCollection());

        try {
            // Check if sequence query is requested
            String sequenceParam = request.getParameter("sequence");
            String bulkSizeParam = request.getParameter("bulkSize");

            if (sequenceParam != null && endpoint.isSequenceEnabled()) {
                // Sequence-based query
                long sequence = Long.parseLong(sequenceParam);
                int bulkSize = bulkSizeParam != null ?
                        Integer.parseInt(bulkSizeParam) :
                        endpoint.getDefaultBulkSize();

                logger.info("Sequence query: sequence={}, bulkSize={}", sequence, bulkSize);
                Map<String, Object> result = engine.queryBySequence(
                    endpoint.getDatabaseCollection(),
                    sequence,
                    bulkSize
                );
                return ResponseEntity.ok(result);

            } else if (sequenceParam != null && !endpoint.isSequenceEnabled()) {
                // Sequence requested but not enabled
                return ResponseEntity.badRequest()
                        .body("Sequence queries are not enabled for this endpoint");

            } else {
                // Regular query (select *)
                List<Document> results = engine.queryCollection(endpoint.getDatabaseCollection());
                return ResponseEntity.ok(results);
            }

        } catch (NumberFormatException e) {
            logger.error("Invalid number format in query parameters", e);
            return ResponseEntity.badRequest()
                    .body("Invalid sequence or bulkSize parameter");
        } catch (Exception e) {
            logger.error("Error handling REST request", e);
            return ResponseEntity.internalServerError()
                    .body("Error querying collection: " + e.getMessage());
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
