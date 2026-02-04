package sigma.controller;

import sigma.config.properties.ZookeeperConfigProperties;
import sigma.model.Endpoint;
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
    private final RestEndpointHandler restEndpointHandler;
    private final GraphQLEndpointHandler graphQLEndpointHandler;

    private String apiPrefix;

    public ApiController(ZookeeperConfigProperties configProperties,
                        EndpointRegistry endpointRegistry,
                        RestEndpointHandler restEndpointHandler,
                        GraphQLEndpointHandler graphQLEndpointHandler) {
        this.configProperties = configProperties;
        this.endpointRegistry = endpointRegistry;
        this.restEndpointHandler = restEndpointHandler;
        this.graphQLEndpointHandler = graphQLEndpointHandler;
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
    @RequestMapping(value = "${apiPrefix:#{'/api'}}/**", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE})
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

        // Strategy pattern - get handler for endpoint type (ZERO switch statements!)
        EndpointHandler handler = endpoint.getType().getHandler(restEndpointHandler, graphQLEndpointHandler);
        return handler.handle(method, relativePath, body, endpoint, request);
    }
}
