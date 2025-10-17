package iaf.ofek.sigma.controller;

import iaf.ofek.sigma.config.properties.ZookeeperConfigProperties;
import iaf.ofek.sigma.model.Endpoint;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Generic API controller that routes requests to specific handlers based on Zookeeper configuration
 * Dynamically handles both REST and GraphQL APIs based on endpoint definitions
 */
@RestController
public class ApiController {

    private static final Logger logger = LoggerFactory.getLogger(ApiController.class);

    private final ZookeeperConfigProperties configProperties;
    private final EndpointRegistry endpointRegistry;
    private final RestApiController restApiController;
    private final GraphQLController graphQLController;

    private String apiPrefix;

    public ApiController(ZookeeperConfigProperties configProperties,
                        EndpointRegistry endpointRegistry,
                        RestApiController restApiController,
                        GraphQLController graphQLController) {
        this.configProperties = configProperties;
        this.endpointRegistry = endpointRegistry;
        this.restApiController = restApiController;
        this.graphQLController = graphQLController;
    }

    @PostConstruct
    public void init() {
        this.apiPrefix = configProperties.getApiPrefix();
        logger.info("ApiController initialized with prefix: {}", apiPrefix);
    }

    /**
     * Handles all requests under the API prefix
     * Routes to appropriate controller based on endpoint configuration
     */
    @RequestMapping(value = "${apiPrefix:#{'/api'}}/**", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE})
    public ResponseEntity<?> handleRequest(@RequestBody(required = false) String body,
                                          HttpServletRequest request) {
        String fullPath = request.getRequestURI();
        String method = request.getMethod();

        logger.debug("Received {} request to: {}", method, fullPath);

        // Extract path relative to API prefix
        String relativePath = fullPath;
        if (fullPath.startsWith(apiPrefix)) {
            relativePath = fullPath.substring(apiPrefix.length());
        }

        // Look up endpoint in registry
        Endpoint endpoint = endpointRegistry.findEndpoint(relativePath, method);

        if (endpoint == null) {
            logger.warn("No endpoint found for {} {}", method, relativePath);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Endpoint not found: " + method + " " + relativePath);
        }

        logger.info("Matched endpoint: {}", endpoint);

        // Route to appropriate controller based on endpoint type
        return switch (endpoint.getType()) {
            case REST -> restApiController.handleRestRequest(method, relativePath, body, endpoint, request);
            case GRAPHQL -> handleGraphQLRequest(body, endpoint);
        };
    }

    /**
     * Handles GraphQL requests
     * Note: GraphQL typically uses @DgsQuery/@DgsMutation annotations
     * This is a fallback for dynamic routing
     */
    private ResponseEntity<?> handleGraphQLRequest(String body, Endpoint endpoint) {
        logger.info("Routing to GraphQL controller for endpoint: {}", endpoint.getName());
        // GraphQL requests will be handled by DGS framework
        // This is a placeholder for custom GraphQL handling
        return ResponseEntity.ok("GraphQL endpoint: " + endpoint.getName());
    }
}
