package iaf.ofek.sigma.controller;

import iaf.ofek.sigma.model.Endpoint;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;

/**
 * Strategy interface for handling different endpoint types
 * Eliminates switch statement in ApiController
 * 
 * Each endpoint type (REST, GraphQL, etc.) has its own handler implementation
 */
public interface EndpointHandler {
    
    /**
     * Handles a request for this endpoint type
     * 
     * @param method HTTP method
     * @param path Relative path
     * @param body Request body
     * @param endpoint Endpoint configuration
     * @param request HTTP request
     * @return Response entity
     */
    ResponseEntity<?> handle(String method, String path, String body, Endpoint endpoint, HttpServletRequest request);
}
