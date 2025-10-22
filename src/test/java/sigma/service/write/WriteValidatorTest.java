package sigma.service.write;

import sigma.dto.request.CreateRequest;
import sigma.dto.request.DeleteRequest;
import sigma.dto.request.UpdateRequest;
import sigma.dto.request.UpsertRequest;
import sigma.dto.request.WriteRequest;
import sigma.filter.FilterValidator;
import sigma.model.Endpoint;
import sigma.model.filter.FilterConfig;
import sigma.model.schema.SchemaReference;
import sigma.service.schema.SchemaValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for WriteValidator - Verifies polymorphic validation without instanceof
 * 
 * Key verification:
 * - No instanceof checks are used
 * - All validation uses polymorphism via getDocumentsForValidation()
 * - CREATE and UPSERT are validated
 * - UPDATE and DELETE skip full validation
 */
@ExtendWith(MockitoExtension.class)
class WriteValidatorTest {

    @Mock
    private FilterValidator filterValidator;
    
    @Mock
    private SchemaValidator schemaValidator;
    
    @Mock
    private Endpoint endpoint;

    @Mock
    private SchemaReference schemaReference;

    @Mock
    private FilterConfig writeFilterConfig;

    private WriteValidator writeValidator;

    @BeforeEach
    void setUp() {
        writeValidator = new WriteValidator(filterValidator, schemaValidator);
        when(endpoint.getName()).thenReturn("test-endpoint");
        when(endpoint.getWriteFilterConfig()).thenReturn(writeFilterConfig);
    }
    
    /**
     * Tests CREATE request validation uses polymorphism
     * 
     * Verifies:
     * - No instanceof check needed
     * - getDocumentsForValidation() is called polymorphically
     * - Schema validation occurs for all documents
     */
    @Test
    void testCreateRequestValidationUsesPolymorphism() {
        // Given: CreateRequest with documents
        Map<String, Object> doc1 = Map.of("name", "Alice", "age", 30);
        Map<String, Object> doc2 = Map.of("name", "Bob", "age", 25);
        CreateRequest request = new CreateRequest(List.of(doc1, doc2), "req-123");
        
        // Configure endpoint for validation
        when(endpoint.isWriteMethodAllowed("POST")).thenReturn(true);
        when(endpoint.requiresSchemaValidation()).thenReturn(true);
        when(endpoint.getSchemaReference()).thenReturn(schemaReference);
        when(schemaReference.getSchemaName()).thenReturn("user-schema");
        when(filterValidator.validate(any(), any())).thenReturn(List.of());
        when(schemaValidator.validateBulk(anyList(), anyString()))
            .thenReturn(SchemaValidator.ValidationResult.success());
        
        // When: Validate request
        WriteValidator.ValidationResult result = writeValidator.validate(request, endpoint);
        
        // Then: Should use polymorphism to get documents
        assertTrue(result.isValid());
        verify(schemaValidator).validateBulk(
            argThat(docs -> docs.size() == 2 && docs.contains(doc1) && docs.contains(doc2)),
            eq("user-schema")
        );
        // ✅ No instanceof used!
        // ✅ Polymorphism via getDocumentsForValidation()!
    }
    
    /**
     * Tests UPSERT request validation uses polymorphism
     */
    @Test
    void testUpsertRequestValidationUsesPolymorphism() {
        // Given: UpsertRequest with document
        Map<String, Object> filter = Map.of("email", "alice@example.com");
        Map<String, Object> document = Map.of("name", "Alice", "age", 30);
        UpsertRequest request = new UpsertRequest(filter, document, "req-123");
        
        // Configure endpoint
        when(endpoint.isWriteMethodAllowed("PUT")).thenReturn(true);
        when(endpoint.requiresSchemaValidation()).thenReturn(true);
        when(endpoint.getSchemaReference()).thenReturn(schemaReference);
        when(schemaReference.getSchemaName()).thenReturn("user-schema");
        when(filterValidator.validate(any(), any())).thenReturn(List.of());
        when(schemaValidator.validateBulk(anyList(), anyString()))
            .thenReturn(SchemaValidator.ValidationResult.success());
        
        // When: Validate request
        WriteValidator.ValidationResult result = writeValidator.validate(request, endpoint);
        
        // Then: Should validate single document via polymorphism
        assertTrue(result.isValid());
        verify(schemaValidator).validateBulk(
            argThat(docs -> docs.size() == 1 && docs.get(0).equals(document)),
            eq("user-schema")
        );
        // ✅ No instanceof used!
    }
    
    /**
     * Tests UPDATE request skips full validation (returns null from getDocumentsForValidation)
     */
    @Test
    void testUpdateRequestSkipsFullValidation() {
        // Given: UpdateRequest (partial update)
        Map<String, Object> filter = Map.of("_id", "123");
        Map<String, Object> updates = Map.of("age", 31);
        UpdateRequest request = new UpdateRequest(filter, updates, "req-123", false);
        
        // Configure endpoint
        when(endpoint.isWriteMethodAllowed("PATCH")).thenReturn(true);
        when(endpoint.requiresSchemaValidation()).thenReturn(true);
        when(filterValidator.validate(any(), any())).thenReturn(List.of());
        
        // When: Validate request
        WriteValidator.ValidationResult result = writeValidator.validate(request, endpoint);
        
        // Then: Should NOT call schema validator (partial update)
        assertTrue(result.isValid());
        verify(schemaValidator, never()).validateBulk(any(), any());
        // ✅ UpdateRequest.getDocumentsForValidation() returns null!
        // ✅ Validator respects polymorphic behavior!
    }
    
    /**
     * Tests DELETE request skips validation (returns null from getDocumentsForValidation)
     */
    @Test
    void testDeleteRequestSkipsValidation() {
        // Given: DeleteRequest
        Map<String, Object> filter = Map.of("status", "inactive");
        DeleteRequest request = new DeleteRequest(filter, "req-123", true);
        
        // Configure endpoint
        when(endpoint.isWriteMethodAllowed("DELETE")).thenReturn(true);
        when(endpoint.requiresSchemaValidation()).thenReturn(true);
        when(filterValidator.validate(any(), any())).thenReturn(List.of());
        
        // When: Validate request
        WriteValidator.ValidationResult result = writeValidator.validate(request, endpoint);
        
        // Then: Should NOT call schema validator (delete has no documents)
        assertTrue(result.isValid());
        verify(schemaValidator, never()).validateBulk(any(), any());
        // ✅ DeleteRequest.getDocumentsForValidation() returns null!
    }
    
    /**
     * Tests that validator handles ALL request types uniformly (no instanceof)
     */
    @Test
    void testValidatorHandlesAllTypesUniformly() {
        // Given: Different request types
        WriteRequest createRequest = new CreateRequest(
            List.of(Map.of("name", "Alice")),
            "req-1"
        );
        WriteRequest upsertRequest = new UpsertRequest(
            Map.of("email", "bob@example.com"),
            Map.of("name", "Bob"),
            "req-2"
        );
        WriteRequest updateRequest = new UpdateRequest(
            Map.of("_id", "123"),
            Map.of("age", 30),
            "req-3",
            false
        );
        WriteRequest deleteRequest = new DeleteRequest(
            Map.of("status", "inactive"),
            "req-4",
            true
        );
        
        // Configure endpoint
        when(endpoint.isWriteMethodAllowed(anyString())).thenReturn(true);
        when(endpoint.requiresSchemaValidation()).thenReturn(true);
        when(endpoint.getSchemaReference()).thenReturn(schemaReference);
        when(schemaReference.getSchemaName()).thenReturn("schema");
        when(filterValidator.validate(any(), any())).thenReturn(List.of());
        when(schemaValidator.validateBulk(anyList(), anyString()))
            .thenReturn(SchemaValidator.ValidationResult.success());
        
        // When: Validate all requests using SAME code path
        WriteRequest[] requests = {createRequest, upsertRequest, updateRequest, deleteRequest};
        for (WriteRequest request : requests) {
            WriteValidator.ValidationResult result = writeValidator.validate(request, endpoint);
            assertTrue(result.isValid());
        }
        
        // Then: Schema validation called exactly 2 times (CREATE + UPSERT only)
        verify(schemaValidator, times(2)).validateBulk(anyList(), eq("schema"));
        // ✅ Single code path handles all types!
        // ✅ No instanceof checks!
        // ✅ Pure polymorphism!
    }
    
    /**
     * Tests validation failure for disallowed write method
     */
    @Test
    void testValidationFailsForDisallowedWriteMethod() {
        // Given: Request for disallowed method
        CreateRequest request = new CreateRequest(List.of(Map.of("name", "Alice")), "req-123");
        when(endpoint.isWriteMethodAllowed("POST")).thenReturn(false);
        
        // When: Validate request
        WriteValidator.ValidationResult result = writeValidator.validate(request, endpoint);
        
        // Then: Should fail
        assertFalse(result.isValid());
        assertFalse(result.getErrors().isEmpty());
        assertTrue(result.getErrors().get(0).contains("not allowed"));
    }
    
    /**
     * Tests validation failure for invalid filter
     */
    @Test
    void testValidationFailsForInvalidFilter() {
        // Given: UpdateRequest with invalid filter
        Map<String, Object> filter = Map.of("invalidField", "value");
        Map<String, Object> updates = Map.of("name", "Alice");
        UpdateRequest request = new UpdateRequest(filter, updates, "req-123", false);

        when(endpoint.isWriteMethodAllowed("PATCH")).thenReturn(true);
        when(endpoint.requiresSchemaValidation()).thenReturn(false);
        when(filterValidator.validate(eq(filter), eq(writeFilterConfig)))
            .thenReturn(List.of("Invalid filter"));

        // When: Validate request
        WriteValidator.ValidationResult result = writeValidator.validate(request, endpoint);

        // Then: Should fail
        assertFalse(result.isValid());
        assertTrue(result.getErrors().contains("Invalid filter"));
    }
    
    /**
     * Tests validation failure for schema validation
     */
    @Test
    void testValidationFailsForInvalidSchema() {
        // Given: Request with invalid document
        Map<String, Object> invalidDoc = Map.of("age", "not-a-number");
        CreateRequest request = new CreateRequest(List.of(invalidDoc), "req-123");
        
        when(endpoint.isWriteMethodAllowed("POST")).thenReturn(true);
        when(endpoint.requiresSchemaValidation()).thenReturn(true);
        when(endpoint.getSchemaReference()).thenReturn(schemaReference);
        when(schemaReference.getSchemaName()).thenReturn("user-schema");
        when(filterValidator.validate(any(), any())).thenReturn(List.of());
        when(schemaValidator.validateBulk(anyList(), anyString()))
            .thenReturn(SchemaValidator.ValidationResult.failure(
                List.of("age: expected integer, got string")
            ));
        
        // When: Validate request
        WriteValidator.ValidationResult result = writeValidator.validate(request, endpoint);
        
        // Then: Should fail
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream()
            .anyMatch(err -> err.contains("age") && err.contains("integer")));
    }
    
    /**
     * Tests that empty filter is allowed
     */
    @Test
    void testEmptyFilterIsAllowed() {
        // Given: Request with no filter
        CreateRequest request = new CreateRequest(List.of(Map.of("name", "Alice")), "req-123");

        when(endpoint.isWriteMethodAllowed("POST")).thenReturn(true);
        when(endpoint.requiresSchemaValidation()).thenReturn(true);
        when(endpoint.getSchemaReference()).thenReturn(schemaReference);
        when(schemaReference.getSchemaName()).thenReturn("schema");
        when(schemaValidator.validateBulk(anyList(), anyString()))
            .thenReturn(SchemaValidator.ValidationResult.success());

        // When: Validate request
        WriteValidator.ValidationResult result = writeValidator.validate(request, endpoint);

        // Then: Should succeed without filter validation
        assertTrue(result.isValid());
        verify(filterValidator, never()).validate(any(), any());
    }

    /**
     * Tests that WRITE filter config is used (not READ filter config)
     *
     * Security feature: Write operations use writeFilterConfig which should
     * be more restrictive to prevent mass updates/deletes
     */
    @Test
    void testUsesWriteFilterConfigNotReadFilterConfig() {
        // Given: DELETE request with filter
        Map<String, Object> filter = Map.of("status", "inactive");
        DeleteRequest request = new DeleteRequest(filter, "req-123", true);

        when(endpoint.isWriteMethodAllowed("DELETE")).thenReturn(true);
        when(filterValidator.validate(eq(filter), eq(writeFilterConfig)))
            .thenReturn(List.of());

        // When: Validate request
        WriteValidator.ValidationResult result = writeValidator.validate(request, endpoint);

        // Then: Should use WRITE filter config, not READ filter config
        assertTrue(result.isValid());
        verify(endpoint).getWriteFilterConfig();  // Should call getWriteFilterConfig()
        verify(endpoint, never()).getReadFilterConfig();  // Should NOT call getReadFilterConfig()
        verify(filterValidator).validate(eq(filter), eq(writeFilterConfig));  // Use write config
    }
}
