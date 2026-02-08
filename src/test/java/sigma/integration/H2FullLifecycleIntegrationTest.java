package sigma.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import sigma.persistence.dialect.DatabaseDialect;
import sigma.persistence.dialect.DatabaseType;
import sigma.persistence.dialect.H2Dialect;
import sigma.persistence.repository.DynamicDocumentRepository;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full lifecycle integration test using H2 database with mocked ZooKeeper.
 *
 * Tests the complete flow:
 * 1. Application startup with H2 database
 * 2. Schema initialization via DatabaseInitializer
 * 3. Create documents via REST API
 * 4. Query documents with various filters
 * 5. Update documents
 * 6. Delete documents
 * 7. Sequence-based pagination
 *
 * ZooKeeper is fully mocked - no external dependencies required.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("h2")
@ContextConfiguration(initializers = TestEnvironmentInitializer.class)
@Import(H2IntegrationTestConfig.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class H2FullLifecycleIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DatabaseDialect databaseDialect;

    @Autowired
    private DynamicDocumentRepository repository;

    private static Long createdProductId1;
    private static Long createdProductId2;
    private static Long createdProductId3;

    // ==================== Startup & Configuration Tests ====================

    @Test
    @Order(1)
    @DisplayName("Application starts with H2 database dialect")
    void testApplicationStartsWithH2Dialect() {
        assertNotNull(databaseDialect, "Database dialect should be configured");
        assertEquals(DatabaseType.H2, databaseDialect.getType(), "Should be using H2 dialect");
        assertInstanceOf(H2Dialect.class, databaseDialect, "Dialect should be H2Dialect instance");
    }

    @Test
    @Order(2)
    @DisplayName("Health endpoint is accessible")
    void testHealthEndpoint() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));
    }

    // ==================== CREATE Tests ====================

    @Test
    @Order(10)
    @DisplayName("Create single product document")
    void testCreateSingleProduct() throws Exception {
        String productJson = """
            {
                "name": "Laptop Pro",
                "category": "electronics",
                "price": 1299.99,
                "inStock": true
            }
            """;

        MvcResult result = mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        Map<String, Object> response = objectMapper.readValue(responseBody,
                new TypeReference<Map<String, Object>>() {});

        assertNotNull(response.get("insertedIds"), "Response should contain insertedIds");
        @SuppressWarnings("unchecked")
        List<String> insertedIds = (List<String>) response.get("insertedIds");
        assertEquals(1, insertedIds.size(), "Should have one inserted ID");

        createdProductId1 = Long.parseLong(insertedIds.get(0));
        assertTrue(createdProductId1 > 0, "Inserted ID should be positive");
    }

    @Test
    @Order(11)
    @DisplayName("Create multiple product documents")
    void testCreateMultipleProducts() throws Exception {
        String productsJson = """
            [
                {
                    "name": "Wireless Mouse",
                    "category": "electronics",
                    "price": 49.99,
                    "inStock": true
                },
                {
                    "name": "USB-C Cable",
                    "category": "accessories",
                    "price": 12.99,
                    "inStock": true
                }
            ]
            """;

        MvcResult result = mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productsJson))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        Map<String, Object> response = objectMapper.readValue(responseBody,
                new TypeReference<Map<String, Object>>() {});

        @SuppressWarnings("unchecked")
        List<String> insertedIds = (List<String>) response.get("insertedIds");
        assertEquals(2, insertedIds.size(), "Should have two inserted IDs");

        createdProductId2 = Long.parseLong(insertedIds.get(0));
        createdProductId3 = Long.parseLong(insertedIds.get(1));
    }

    // ==================== QUERY Tests ====================

    @Test
    @Order(20)
    @DisplayName("Query all products with GET request")
    void testQueryAllProducts() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        List<Map<String, Object>> products = objectMapper.readValue(responseBody,
                new TypeReference<List<Map<String, Object>>>() {});

        assertTrue(products.size() >= 3, "Should have at least 3 products");
    }

    @Test
    @Order(21)
    @DisplayName("Query products by category with filter")
    void testQueryProductsByCategory() throws Exception {
        String filterJson = """
            {
                "filter": {
                    "category": {"eq": "electronics"}
                }
            }
            """;

        MvcResult result = mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(filterJson))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        List<Map<String, Object>> products = objectMapper.readValue(responseBody,
                new TypeReference<List<Map<String, Object>>>() {});

        assertTrue(products.size() >= 2, "Should have at least 2 electronics products");
        for (Map<String, Object> product : products) {
            assertEquals("electronics", product.get("category"),
                    "All returned products should be in electronics category");
        }
    }

    @Test
    @Order(22)
    @DisplayName("Query products with price range filter")
    void testQueryProductsByPriceRange() throws Exception {
        String filterJson = """
            {
                "filter": {
                    "price": {"gte": 40, "lte": 100}
                }
            }
            """;

        MvcResult result = mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(filterJson))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        List<Map<String, Object>> products = objectMapper.readValue(responseBody,
                new TypeReference<List<Map<String, Object>>>() {});

        for (Map<String, Object> product : products) {
            double price = ((Number) product.get("price")).doubleValue();
            assertTrue(price >= 40 && price <= 100,
                    "Product price should be between 40 and 100: " + price);
        }
    }

    @Test
    @Order(23)
    @DisplayName("Query products with IN operator")
    void testQueryProductsWithInOperator() throws Exception {
        String filterJson = """
            {
                "filter": {
                    "category": {"in": ["electronics", "accessories"]}
                }
            }
            """;

        MvcResult result = mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(filterJson))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        List<Map<String, Object>> products = objectMapper.readValue(responseBody,
                new TypeReference<List<Map<String, Object>>>() {});

        assertTrue(products.size() >= 3, "Should return all 3 products");
        for (Map<String, Object> product : products) {
            String category = (String) product.get("category");
            assertTrue(category.equals("electronics") || category.equals("accessories"),
                    "Category should be electronics or accessories");
        }
    }

    @Test
    @Order(24)
    @DisplayName("Query products with sorting and pagination")
    void testQueryProductsWithSortingAndPagination() throws Exception {
        String filterJson = """
            {
                "options": {
                    "sort": {"price": -1},
                    "limit": 2
                }
            }
            """;

        MvcResult result = mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(filterJson))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        List<Map<String, Object>> products = objectMapper.readValue(responseBody,
                new TypeReference<List<Map<String, Object>>>() {});

        assertEquals(2, products.size(), "Should return exactly 2 products");

        // Verify descending sort order
        if (products.size() >= 2) {
            double price1 = ((Number) products.get(0).get("price")).doubleValue();
            double price2 = ((Number) products.get(1).get("price")).doubleValue();
            assertTrue(price1 >= price2, "Products should be sorted by price descending");
        }
    }

    @Test
    @Order(25)
    @DisplayName("Query products with AND logical operator")
    void testQueryProductsWithAndOperator() throws Exception {
        String filterJson = """
            {
                "filter": {
                    "and": [
                        {"category": {"eq": "electronics"}},
                        {"price": {"lt": 100}}
                    ]
                }
            }
            """;

        MvcResult result = mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(filterJson))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        List<Map<String, Object>> products = objectMapper.readValue(responseBody,
                new TypeReference<List<Map<String, Object>>>() {});

        for (Map<String, Object> product : products) {
            assertEquals("electronics", product.get("category"));
            double price = ((Number) product.get("price")).doubleValue();
            assertTrue(price < 100, "Price should be less than 100");
        }
    }

    // ==================== UPDATE Tests ====================

    @Test
    @Order(30)
    @DisplayName("Update product by ID")
    void testUpdateProductById() throws Exception {
        assertNotNull(createdProductId1, "Product ID should be set from creation test");

        String updateJson = String.format("""
            {
                "filter": {"id": {"eq": %d}},
                "update": {"price": 1199.99, "onSale": true}
            }
            """, createdProductId1);

        MvcResult result = mockMvc.perform(put("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        Map<String, Object> response = objectMapper.readValue(responseBody,
                new TypeReference<Map<String, Object>>() {});

        assertEquals(1, ((Number) response.get("matchedCount")).intValue(),
                "Should match 1 document");
        assertEquals(1, ((Number) response.get("modifiedCount")).intValue(),
                "Should modify 1 document");

        // Verify the update
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> documents = (List<Map<String, Object>>) response.get("documents");
        assertFalse(documents.isEmpty(), "Should return updated document");

        Map<String, Object> updatedProduct = documents.get(0);
        assertEquals(1199.99, ((Number) updatedProduct.get("price")).doubleValue(), 0.01,
                "Price should be updated");
        assertEquals(true, updatedProduct.get("onSale"), "onSale should be true");
    }

    // ==================== DELETE Tests ====================

    @Test
    @Order(40)
    @DisplayName("Delete product by ID (soft delete)")
    void testDeleteProductById() throws Exception {
        assertNotNull(createdProductId3, "Product ID 3 should be set from creation test");

        String deleteJson = String.format("""
            {
                "filter": {"id": {"eq": %d}}
            }
            """, createdProductId3);

        MvcResult result = mockMvc.perform(delete("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(deleteJson))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        Map<String, Object> response = objectMapper.readValue(responseBody,
                new TypeReference<Map<String, Object>>() {});

        assertEquals(1, ((Number) response.get("deletedCount")).intValue(),
                "Should delete 1 document");

        // Verify the product is no longer returned in queries
        String verifyFilter = String.format("""
            {
                "filter": {"id": {"eq": %d}}
            }
            """, createdProductId3);

        MvcResult verifyResult = mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyFilter))
                .andExpect(status().isOk())
                .andReturn();

        String verifyBody = verifyResult.getResponse().getContentAsString();
        List<Map<String, Object>> products = objectMapper.readValue(verifyBody,
                new TypeReference<List<Map<String, Object>>>() {});

        assertTrue(products.isEmpty(), "Deleted product should not be returned");
    }

    // ==================== Sequence Pagination Tests ====================

    @Test
    @Order(50)
    @DisplayName("Query products with sequence-based pagination")
    void testSequenceBasedPagination() throws Exception {
        // First page
        MvcResult result1 = mockMvc.perform(get("/api/products")
                        .param("sequence", "0")
                        .param("bulkSize", "1"))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody1 = result1.getResponse().getContentAsString();
        Map<String, Object> response1 = objectMapper.readValue(responseBody1,
                new TypeReference<Map<String, Object>>() {});

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data1 = (List<Map<String, Object>>) response1.get("data");
        assertNotNull(data1, "Response should contain data");
        assertFalse(data1.isEmpty(), "First page should have data");

        Long nextSequence = ((Number) response1.get("nextSequence")).longValue();
        assertNotNull(nextSequence, "Should have nextSequence for pagination");
        assertTrue(nextSequence > 0, "Next sequence should be positive");

        // Second page using nextSequence
        MvcResult result2 = mockMvc.perform(get("/api/products")
                        .param("sequence", String.valueOf(nextSequence))
                        .param("bulkSize", "1"))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody2 = result2.getResponse().getContentAsString();
        Map<String, Object> response2 = objectMapper.readValue(responseBody2,
                new TypeReference<Map<String, Object>>() {});

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data2 = (List<Map<String, Object>>) response2.get("data");

        // Verify different documents on each page
        if (!data1.isEmpty() && !data2.isEmpty()) {
            assertNotEquals(data1.get(0).get("id"), data2.get(0).get("id"),
                    "Pagination should return different documents");
        }
    }

    // ==================== Validation & Error Tests ====================

    @Test
    @Order(60)
    @DisplayName("Query with invalid filter operator returns error")
    void testInvalidFilterOperatorReturnsError() throws Exception {
        // Try to use regex on price field (not allowed)
        String filterJson = """
            {
                "filter": {
                    "price": {"regex": "100"}
                }
            }
            """;

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(filterJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(61)
    @DisplayName("Query non-existent endpoint returns 404")
    void testNonExistentEndpointReturns404() throws Exception {
        mockMvc.perform(get("/api/nonexistent"))
                .andExpect(status().isNotFound());
    }

    // ==================== Users Endpoint Tests ====================

    @Test
    @Order(70)
    @DisplayName("Create and query user documents")
    void testCreateAndQueryUsers() throws Exception {
        // Create a user
        String userJson = """
            {
                "username": "testuser",
                "email": "test@example.com",
                "status": "active"
            }
            """;

        MvcResult createResult = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userJson))
                .andExpect(status().isOk())
                .andReturn();

        String createBody = createResult.getResponse().getContentAsString();
        Map<String, Object> createResponse = objectMapper.readValue(createBody,
                new TypeReference<Map<String, Object>>() {});

        @SuppressWarnings("unchecked")
        List<String> insertedIds = (List<String>) createResponse.get("insertedIds");
        assertFalse(insertedIds.isEmpty(), "Should have inserted user");

        // Query by username
        String filterJson = """
            {
                "filter": {
                    "username": {"eq": "testuser"}
                }
            }
            """;

        MvcResult queryResult = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(filterJson))
                .andExpect(status().isOk())
                .andReturn();

        String queryBody = queryResult.getResponse().getContentAsString();
        List<Map<String, Object>> users = objectMapper.readValue(queryBody,
                new TypeReference<List<Map<String, Object>>>() {});

        assertEquals(1, users.size(), "Should find exactly one user");
        assertEquals("testuser", users.get(0).get("username"));
        assertEquals("test@example.com", users.get(0).get("email"));
        assertEquals("active", users.get(0).get("status"));
    }

    // ==================== Repository Direct Access Tests ====================

    @Test
    @Order(80)
    @DisplayName("Repository correctly uses H2 dialect for JSON operations")
    void testRepositoryUsesH2DialectForJsonOperations() {
        // Verify the repository is using the correct dialect
        List<Map<String, Object>> allProducts = repository.findAll("products");

        // This should work without errors if H2 dialect is correctly configured
        assertNotNull(allProducts, "Repository should return results");
        assertTrue(allProducts.size() >= 2, "Should have at least 2 products (one was deleted)");

        // Verify JSON fields are properly extracted
        for (Map<String, Object> product : allProducts) {
            assertNotNull(product.get("name"), "Product should have name");
            assertTrue(product.containsKey("price"), "Product should have price");
        }
    }
}
