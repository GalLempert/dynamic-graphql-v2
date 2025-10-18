package iaf.ofek.sigma.controller;

import iaf.ofek.sigma.model.Endpoint;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * Handler for GraphQL endpoints
 * Strategy pattern implementation
 */
@Component
public class GraphQLEndpointHandler implements EndpointHandler {

    private static final Logger logger = LoggerFactory.getLogger(GraphQLEndpointHandler.class);

    @Override
    public ResponseEntity<?> handle(String method, String path, String body, Endpoint endpoint, HttpServletRequest request) {
        logger.info("Routing to GraphQL controller for endpoint: {}", endpoint.getName());
        // GraphQL requests will be handled by DGS framework
        // This is a placeholder for custom GraphQL handling
        return ResponseEntity.ok("GraphQL endpoint: " + endpoint.getName());
    }
}
