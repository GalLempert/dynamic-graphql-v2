package sigma.console;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the read-only console API in local dev mode (no mocks).
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "sigma.local-config.file=classpath:local-mode-test-config.json",
                "spring.datasource.url=jdbc:h2:mem:sigma-console-test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
                "sigma.console.enabled=true",
                // explicitly read-only: the explorer must work without the editor
                "sigma.console.edit.enabled=false"
        })
@AutoConfigureMockMvc
@ActiveProfiles("local")
class ConsoleApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Info reports service identity and runtime facts")
    void testInfo() throws Exception {
        MvcResult result = mockMvc.perform(get("/console/api/info"))
                .andExpect(status().isOk())
                .andReturn();

        Map<String, Object> info = objectMapper.readValue(result.getResponse().getContentAsString(),
                new TypeReference<Map<String, Object>>() {});
        assertEquals("test", info.get("env"));
        assertEquals("dynamic-service", info.get("service"));
        assertEquals("/api", info.get("apiPrefix"));
        assertEquals("H2", info.get("databaseType"));
        assertEquals(Boolean.FALSE, info.get("editable"), "editing is not enabled in this test");
    }

    @Test
    @DisplayName("Resource definitions are listed even in read-only mode")
    void testResourcesAvailableReadOnly() throws Exception {
        MvcResult result = mockMvc.perform(get("/console/api/resources"))
                .andExpect(status().isOk())
                .andReturn();

        List<Map<String, Object>> resources = objectMapper.readValue(result.getResponse().getContentAsString(),
                new TypeReference<List<Map<String, Object>>>() {});
        assertEquals(1, resources.size());
        assertEquals("gadgets", resources.get(0).get("name"));
    }

    @Test
    @DisplayName("Endpoints are listed once each, with methods and filter config")
    void testEndpoints() throws Exception {
        MvcResult result = mockMvc.perform(get("/console/api/endpoints"))
                .andExpect(status().isOk())
                .andReturn();

        List<Map<String, Object>> endpoints = objectMapper.readValue(result.getResponse().getContentAsString(),
                new TypeReference<List<Map<String, Object>>>() {});

        assertEquals(1, endpoints.size(), "Multi-method endpoints must be reported once");
        Map<String, Object> gadgets = endpoints.get(0);
        assertEquals("gadgets", gadgets.get("name"));
        assertEquals("/gadgets", gadgets.get("path"));
        assertEquals(List.of("GET", "POST"), gadgets.get("methods"));
        assertEquals(List.of("POST"), gadgets.get("writeMethods"));

        @SuppressWarnings("unchecked")
        Map<String, Object> readFilter = (Map<String, Object>) gadgets.get("readFilter");
        assertEquals(Boolean.TRUE, readFilter.get("enabled"));
        @SuppressWarnings("unchecked")
        Map<String, List<String>> fields = (Map<String, List<String>>) readFilter.get("fields");
        assertEquals(List.of("eq"), fields.get("name"));
    }

    @Test
    @DisplayName("Config tree is exposed with secret values redacted")
    void testConfigTreeRedaction() throws Exception {
        MvcResult result = mockMvc.perform(get("/console/api/config"))
                .andExpect(status().isOk())
                .andReturn();

        Map<String, String> tree = objectMapper.readValue(result.getResponse().getContentAsString(),
                new TypeReference<Map<String, String>>() {});

        assertEquals("/api", tree.get("/test/dynamic-service/apiPrefix"));
        assertEquals("•••••", tree.get("/test/dataSource/postgresql.password"),
                "Secret-looking config values must be redacted");
        assertFalse(tree.containsValue("super-secret-value"),
                "The raw secret must never appear in the response");
    }
}
