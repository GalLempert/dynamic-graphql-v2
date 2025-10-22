package sigma.controller;

import sigma.dto.request.QueryRequest;
import sigma.dto.request.WriteRequest;
import sigma.dto.response.QueryResponse;
import sigma.dto.response.Response;
import sigma.model.Endpoint;
import sigma.service.Orchestrator;
import sigma.service.request.RequestParser;
import sigma.service.response.ResponseBuilder;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests for RestApiController - Focus on POST routing logic
 * 
 * Critical bug fix verification:
 * - POST should route to READ when endpoint doesn't allow POST for writes
 * - POST should route to WRITE when endpoint allows POST for writes
 */
@ExtendWith(MockitoExtension.class)
class RestApiControllerTest {

    @Mock
    private RequestParser requestParser;
    
    @Mock
    private Orchestrator orchestrator;
    
    @Mock
    private ResponseBuilder responseBuilder;
    
    @Mock
    private HttpServletRequest httpRequest;
    
    @Mock
    private Endpoint endpoint;
    
    @Mock
    private QueryRequest queryRequest;
    
    @Mock
    private WriteRequest writeRequest;
    
    @Mock
    private QueryResponse queryResponse;
    
    @Mock
    private Response writeResponse;
    
    private RestApiController controller;
    
    @BeforeEach
    void setUp() {
        controller = new RestApiController(requestParser, orchestrator, responseBuilder);
        when(endpoint.getName()).thenReturn("test-endpoint");
    }
    
    /**
     * Tests the critical bug fix: POST routing for filtered reads
     * 
     * When endpoint does NOT have POST in writeMethods:
     * - POST should be treated as a READ operation
     * - Should call requestParser.parse() (not parseWrite())
     * - Should call orchestrator.executeQuery() (not executeWrite())
     */
    @Test
    void testPostRoutingForFilteredReads() {
        // Given: Endpoint WITHOUT POST in writeMethods (only PUT, PATCH)
        when(endpoint.isWriteMethodAllowed("POST")).thenReturn(false);
        when(requestParser.parse(eq("POST"), any(), any(), any())).thenReturn(queryRequest);
        when(orchestrator.executeQuery(any(), any())).thenReturn(queryResponse);
        when(responseBuilder.build(any())).thenReturn(ResponseEntity.ok().build());
        
        // When: POST request received
        ResponseEntity<?> response = controller.handleRestRequest(
            "POST",
            "/test",
            "{\"filter\": {\"category\": \"electronics\"}}",
            endpoint,
            httpRequest
        );
        
        // Then: Should be treated as READ operation
        verify(requestParser).parse(eq("POST"), any(), any(), eq(endpoint));
        verify(requestParser, never()).parseWrite(any(), any(), any(), any());
        verify(orchestrator).executeQuery(any(), eq(endpoint));
        verify(orchestrator, never()).executeWrite(any(), any());
        verify(responseBuilder).build(any());
        assertNotNull(response);
    }
    
    /**
     * Tests the critical bug fix: POST routing for CREATE writes
     * 
     * When endpoint HAS POST in writeMethods:
     * - POST should be treated as a WRITE operation
     * - Should call requestParser.parseWrite() (not parse())
     * - Should call orchestrator.executeWrite() (not executeQuery())
     */
    @Test
    void testPostRoutingForCreateWrites() {
        // Given: Endpoint WITH POST in writeMethods
        when(endpoint.isWriteMethodAllowed("POST")).thenReturn(true);
        when(requestParser.parseWrite(eq("POST"), any(), any(), any())).thenReturn(writeRequest);
        when(orchestrator.executeWrite(any(), any())).thenReturn(writeResponse);
        when(responseBuilder.buildWrite(any())).thenReturn(ResponseEntity.ok().build());
        
        // When: POST request received
        ResponseEntity<?> response = controller.handleRestRequest(
            "POST",
            "/test",
            "{\"name\": \"Alice\", \"email\": \"alice@example.com\"}",
            endpoint,
            httpRequest
        );
        
        // Then: Should be treated as WRITE operation
        verify(requestParser).parseWrite(eq("POST"), any(), any(), eq(endpoint));
        verify(requestParser, never()).parse(any(), any(), any(), any());
        verify(orchestrator).executeWrite(any(), eq(endpoint));
        verify(orchestrator, never()).executeQuery(any(), any());
        verify(responseBuilder).buildWrite(any());
        assertNotNull(response);
    }
    
    /**
     * Tests that GET is always treated as READ
     */
    @Test
    void testGetAlwaysReads() {
        // Given: Any endpoint configuration
        when(requestParser.parse(eq("GET"), any(), any(), any())).thenReturn(queryRequest);
        when(orchestrator.executeQuery(any(), any())).thenReturn(queryResponse);
        when(responseBuilder.build(any())).thenReturn(ResponseEntity.ok().build());
        
        // When: GET request received
        controller.handleRestRequest("GET", "/test", null, endpoint, httpRequest);
        
        // Then: Should always be READ, never check writeMethods
        verify(endpoint, never()).isWriteMethodAllowed(any());
        verify(orchestrator).executeQuery(any(), eq(endpoint));
        verify(orchestrator, never()).executeWrite(any(), any());
    }
    
    /**
     * Tests that PUT is checked against writeMethods
     */
    @Test
    void testPutCheckWriteMethods() {
        // Given: Endpoint with PUT in writeMethods
        when(endpoint.isWriteMethodAllowed("PUT")).thenReturn(true);
        when(requestParser.parseWrite(eq("PUT"), any(), any(), any())).thenReturn(writeRequest);
        when(orchestrator.executeWrite(any(), any())).thenReturn(writeResponse);
        when(responseBuilder.buildWrite(any())).thenReturn(ResponseEntity.ok().build());
        
        // When: PUT request received
        controller.handleRestRequest("PUT", "/test", "{}", endpoint, httpRequest);
        
        // Then: Should check writeMethods and route to WRITE
        verify(endpoint).isWriteMethodAllowed("PUT");
        verify(orchestrator).executeWrite(any(), eq(endpoint));
    }
    
    /**
     * Tests that PATCH is checked against writeMethods
     */
    @Test
    void testPatchCheckWriteMethods() {
        // Given: Endpoint with PATCH in writeMethods
        when(endpoint.isWriteMethodAllowed("PATCH")).thenReturn(true);
        when(requestParser.parseWrite(eq("PATCH"), any(), any(), any())).thenReturn(writeRequest);
        when(orchestrator.executeWrite(any(), any())).thenReturn(writeResponse);
        when(responseBuilder.buildWrite(any())).thenReturn(ResponseEntity.ok().build());
        
        // When: PATCH request received
        controller.handleRestRequest("PATCH", "/test", "{}", endpoint, httpRequest);
        
        // Then: Should check writeMethods and route to WRITE
        verify(endpoint).isWriteMethodAllowed("PATCH");
        verify(orchestrator).executeWrite(any(), eq(endpoint));
    }
    
    /**
     * Tests that DELETE is checked against writeMethods
     */
    @Test
    void testDeleteCheckWriteMethods() {
        // Given: Endpoint with DELETE in writeMethods
        when(endpoint.isWriteMethodAllowed("DELETE")).thenReturn(true);
        when(requestParser.parseWrite(eq("DELETE"), any(), any(), any())).thenReturn(writeRequest);
        when(orchestrator.executeWrite(any(), any())).thenReturn(writeResponse);
        when(responseBuilder.buildWrite(any())).thenReturn(ResponseEntity.ok().build());
        
        // When: DELETE request received
        controller.handleRestRequest("DELETE", "/test", null, endpoint, httpRequest);
        
        // Then: Should check writeMethods and route to WRITE
        verify(endpoint).isWriteMethodAllowed("DELETE");
        verify(orchestrator).executeWrite(any(), eq(endpoint));
    }
}
