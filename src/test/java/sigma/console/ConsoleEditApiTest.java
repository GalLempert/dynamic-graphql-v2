package sigma.console;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end test of the console publish flow, no mocks:
 * a resource created through the console API becomes a live, working
 * API endpoint in the same running application, and the definition is
 * committed to the Git-backed store.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "sigma.local-config.file=classpath:local-mode-test-config.json",
                "spring.datasource.url=jdbc:h2:mem:sigma-console-edit-test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
                "sigma.console.enabled=true",
                "sigma.console.edit.enabled=true",
                "sigma.console.resources-dir=target/console-edit-test-resources"
        })
@AutoConfigureMockMvc
@ActiveProfiles("local")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ConsoleEditApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String ORDERS_RESOURCE = """
        {
            "name": "orders",
            "description": "Customer orders",
            "actions": {"read": true, "add": true, "change": false, "remove": false},
            "search": {
                "status": ["equals", "one of"],
                "total": ["at least", "at most"]
            },
            "changeBy": {},
            "features": {"changeFeed": false, "pageSize": 100}
        }
        """;

    @Test
    @Order(1)
    @DisplayName("Validation rejects a broken definition with product-language problems")
    void testValidateRejects() throws Exception {
        String broken = """
            {"name": "Bad Name", "actions": {"read": false, "add": false, "change": false, "remove": false}}
            """;
        MvcResult result = mockMvc.perform(post("/console/api/resources/validate")
                        .contentType(MediaType.APPLICATION_JSON).content(broken))
                .andExpect(status().isOk())
                .andReturn();

        Map<String, Object> validation = objectMapper.readValue(result.getResponse().getContentAsString(),
                new TypeReference<Map<String, Object>>() {});
        assertEquals(Boolean.FALSE, validation.get("valid"));
        @SuppressWarnings("unchecked")
        List<String> problems = (List<String>) validation.get("problems");
        assertFalse(problems.isEmpty());
    }

    @Test
    @Order(2)
    @DisplayName("Publishing without a reason is refused")
    void testPublishRequiresReason() throws Exception {
        String body = "{\"resource\": " + ORDERS_RESOURCE + ", \"reason\": \"\", \"author\": \"pm\"}";
        mockMvc.perform(post("/console/api/resources")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(3)
    @DisplayName("A published resource becomes a live API endpoint immediately")
    void testPublishGoesLive() throws Exception {
        // the endpoint does not exist yet
        mockMvc.perform(get("/api/orders")).andExpect(status().isNotFound());

        // publish through the console
        String body = "{\"resource\": " + ORDERS_RESOURCE
                + ", \"reason\": \"Launch order tracking\", \"author\": \"product-team\"}";
        MvcResult publish = mockMvc.perform(post("/console/api/resources")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn();

        Map<String, Object> result = objectMapper.readValue(publish.getResponse().getContentAsString(),
                new TypeReference<Map<String, Object>>() {});
        assertEquals(Boolean.TRUE, result.get("published"));
        assertNotNull(result.get("commit"), "publish must record a Git commit");

        // the definition is stored as YAML in the Git-backed store
        Path yaml = Path.of("target/console-edit-test-resources/orders.yaml");
        assertTrue(Files.exists(yaml), "orders.yaml must be written");
        assertTrue(Files.readString(yaml).contains("Customer orders"));

        // ... and the endpoint is live in the same running application
        mockMvc.perform(get("/api/orders")).andExpect(status().isOk());

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"open\", \"total\": 42.5}"))
                .andExpect(status().isCreated());

        // search with an allowed condition works
        MvcResult query = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"filter\": {\"status\": {\"eq\": \"open\"}}}"))
                .andExpect(status().isOk())
                .andReturn();
        List<Map<String, Object>> orders = objectMapper.readValue(query.getResponse().getContentAsString(),
                new TypeReference<List<Map<String, Object>>>() {});
        assertEquals(1, orders.size());
        assertEquals("open", orders.get(0).get("status"));

        // a condition that was not granted is rejected
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"filter\": {\"status\": {\"regex\": \"op.*\"}}}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(4)
    @DisplayName("The published resource appears in the console list as a definition")
    void testResourceListedAfterPublish() throws Exception {
        MvcResult result = mockMvc.perform(get("/console/api/resources"))
                .andExpect(status().isOk())
                .andReturn();

        List<Map<String, Object>> resources = objectMapper.readValue(result.getResponse().getContentAsString(),
                new TypeReference<List<Map<String, Object>>>() {});

        Map<String, Object> orders = resources.stream()
                .filter(r -> "orders".equals(r.get("name")))
                .findFirst().orElseThrow(() -> new AssertionError("orders resource missing from list"));
        assertEquals("Customer orders", orders.get("description"));

        @SuppressWarnings("unchecked")
        Map<String, Object> search = (Map<String, Object>) orders.get("search");
        assertEquals(List.of("equals", "one of"), search.get("status"));
        assertEquals(List.of("at least", "at most"), search.get("total"));
    }
}
