package iaf.ofek.sigma.controller;

import iaf.ofek.sigma.model.Endpoint;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * Handler for REST endpoints
 * Strategy pattern implementation
 */
@Component
public class RestEndpointHandler implements EndpointHandler {

    private final RestApiController restApiController;

    public RestEndpointHandler(RestApiController restApiController) {
        this.restApiController = restApiController;
    }

    @Override
    public ResponseEntity<?> handle(String method, String path, String body, Endpoint endpoint, HttpServletRequest request) {
        return restApiController.handleRestRequest(method, path, body, endpoint, request);
    }
}
