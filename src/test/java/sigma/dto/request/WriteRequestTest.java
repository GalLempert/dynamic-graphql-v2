package sigma.dto.request;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for WriteRequest polymorphic validation
 * 
 * Verifies the Template Method pattern implementation:
 * - getDocumentsForValidation() provides polymorphic behavior
 * - No instanceof checks needed
 * - Each request type returns appropriate documents
 */
class WriteRequestTest {
    
    /**
     * Tests CreateRequest returns all documents for validation
     */
    @Test
    void testCreateRequestReturnsDocumentsForValidation() {
        // Given: CreateRequest with multiple documents
        Map<String, Object> doc1 = Map.of("name", "Alice", "age", 30);
        Map<String, Object> doc2 = Map.of("name", "Bob", "age", 25);
        List<Map<String, Object>> documents = List.of(doc1, doc2);
        
        CreateRequest request = new CreateRequest(documents, "req-123");
        
        // When: Get documents for validation
        List<Map<String, Object>> result = request.getDocumentsForValidation();
        
        // Then: Should return all documents
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(documents, result);
    }
    
    /**
     * Tests UpsertRequest returns single document for validation
     */
    @Test
    void testUpsertRequestReturnsDocumentForValidation() {
        // Given: UpsertRequest with single document
        Map<String, Object> filter = Map.of("email", "alice@example.com");
        Map<String, Object> document = Map.of("name", "Alice Updated", "age", 31);
        
        UpsertRequest request = new UpsertRequest(filter, document, "req-123");
        
        // When: Get documents for validation
        List<Map<String, Object>> result = request.getDocumentsForValidation();
        
        // Then: Should return list with single document
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(document, result.get(0));
    }
    
    /**
     * Tests UpdateRequest returns null (no full validation needed)
     */
    @Test
    void testUpdateRequestReturnsNullForValidation() {
        // Given: UpdateRequest (partial update)
        Map<String, Object> filter = Map.of("_id", "123");
        Map<String, Object> updates = Map.of("age", 31);
        
        UpdateRequest request = new UpdateRequest(filter, updates, "req-123", false);
        
        // When: Get documents for validation
        List<Map<String, Object>> result = request.getDocumentsForValidation();
        
        // Then: Should return null (partial updates don't need full schema validation)
        assertNull(result);
    }
    
    /**
     * Tests DeleteRequest returns null (no validation needed)
     */
    @Test
    void testDeleteRequestReturnsNullForValidation() {
        // Given: DeleteRequest
        Map<String, Object> filter = Map.of("status", "inactive");
        
        DeleteRequest request = new DeleteRequest(filter, "req-123", false);
        
        // When: Get documents for validation
        List<Map<String, Object>> result = request.getDocumentsForValidation();
        
        // Then: Should return null (deletes don't need validation)
        assertNull(result);
    }
    
    /**
     * Tests polymorphic behavior - no instanceof needed
     * 
     * This test demonstrates the OOP fix:
     * - Can treat all WriteRequests uniformly
     * - No type checking or casting required
     * - Polymorphism handles the differences
     */
    @Test
    void testPolymorphicBehaviorWithoutInstanceof() {
        // Given: Multiple WriteRequest types
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
        
        // When: Get documents for validation from each (no instanceof!)
        List<Map<String, Object>> createDocs = createRequest.getDocumentsForValidation();
        List<Map<String, Object>> upsertDocs = upsertRequest.getDocumentsForValidation();
        List<Map<String, Object>> updateDocs = updateRequest.getDocumentsForValidation();
        List<Map<String, Object>> deleteDocs = deleteRequest.getDocumentsForValidation();
        
        // Then: Each returns appropriate result via polymorphism
        assertNotNull(createDocs);
        assertEquals(1, createDocs.size());
        
        assertNotNull(upsertDocs);
        assertEquals(1, upsertDocs.size());
        
        assertNull(updateDocs);  // Partial update, no validation
        assertNull(deleteDocs);  // Delete, no validation
        
        // ✅ No instanceof used!
        // ✅ All handled polymorphically!
    }
    
    /**
     * Tests that validation logic can be written generically
     */
    @Test
    void testGenericValidationLogic() {
        // Given: Array of different WriteRequest types
        WriteRequest[] requests = {
            new CreateRequest(List.of(Map.of("name", "Alice")), "req-1"),
            new UpsertRequest(Map.of("id", "1"), Map.of("name", "Bob"), "req-2"),
            new UpdateRequest(Map.of("id", "2"), Map.of("age", 30), "req-3", false),
            new DeleteRequest(Map.of("id", "3"), "req-4", false)
        };
        
        // When: Process all requests with SAME generic code (no instanceof!)
        int documentsToValidate = 0;
        for (WriteRequest request : requests) {
            List<Map<String, Object>> docs = request.getDocumentsForValidation();
            if (docs != null && !docs.isEmpty()) {
                documentsToValidate += docs.size();
            }
        }
        
        // Then: Should count documents correctly via polymorphism
        assertEquals(2, documentsToValidate);  // 1 from CREATE + 1 from UPSERT
        
        // ✅ Single code path for all types!
        // ✅ No switch/if-else based on type!
        // ✅ Pure OOP polymorphism!
    }
    
    /**
     * Tests CreateRequest with empty list
     */
    @Test
    void testCreateRequestWithEmptyList() {
        // Given: CreateRequest with empty document list
        CreateRequest request = new CreateRequest(List.of(), "req-123");
        
        // When: Get documents for validation
        List<Map<String, Object>> result = request.getDocumentsForValidation();
        
        // Then: Should return empty list (not null)
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
    
    /**
     * Tests UpsertRequest with empty document
     */
    @Test
    void testUpsertRequestWithEmptyDocument() {
        // Given: UpsertRequest with empty document
        Map<String, Object> emptyDoc = new HashMap<>();
        UpsertRequest request = new UpsertRequest(Map.of(), emptyDoc, "req-123");
        
        // When: Get documents for validation
        List<Map<String, Object>> result = request.getDocumentsForValidation();
        
        // Then: Should still return list with the document
        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(result.get(0).isEmpty());
    }
}
