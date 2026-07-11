package sigma.config.local;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import sigma.persistence.dialect.DatabaseDialect;
import sigma.persistence.dialect.DatabaseType;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Boots the application in local dev mode with NO mocks:
 * endpoint configuration comes from a JSON file (no ZooKeeper) and the
 * database is in-memory H2, exactly as "mvn spring-boot:run -Dspring-boot.run.profiles=local".
 *
 * ENV=test / SERVICE=dynamic-service are provided by the surefire configuration,
 * so the seed file uses the /test/dynamic-service tree.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "sigma.local-config.file=classpath:local-mode-test-config.json",
                "spring.datasource.url=jdbc:h2:mem:sigma-local-smoke;DB_CLOSE_DELAY=-1;MODE=PostgreSQL"
        })
@AutoConfigureMockMvc
@ActiveProfiles("local")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LocalModeSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DatabaseDialect databaseDialect;

    @Test
    @Order(1)
    @DisplayName("Local mode starts with the H2 dialect and no ZooKeeper")
    void testStartsWithH2Dialect() {
        assertEquals(DatabaseType.H2, databaseDialect.getType());
    }

    @Test
    @Order(2)
    @DisplayName("Endpoint from the local config file is routable")
    void testFileConfiguredEndpointRoutes() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/gadgets"))
                .andExpect(status().isOk())
                .andReturn();

        List<Map<String, Object>> gadgets = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                new TypeReference<List<Map<String, Object>>>() {});
        assertNotNull(gadgets);
    }

    @Test
    @Order(3)
    @DisplayName("Create and query a document through a file-configured endpoint")
    void testCreateAndQuery() throws Exception {
        String gadgetJson = """
            {
                "name": "Widget",
                "color": "blue"
            }
            """;

        MvcResult createResult = mockMvc.perform(post("/api/gadgets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(gadgetJson))
                .andExpect(status().isCreated())
                .andReturn();

        Map<String, Object> createResponse = objectMapper.readValue(
                createResult.getResponse().getContentAsString(),
                new TypeReference<Map<String, Object>>() {});
        @SuppressWarnings("unchecked")
        List<String> insertedIds = (List<String>) createResponse.get("insertedIds");
        assertEquals(1, insertedIds.size());

        String filterJson = """
            {
                "filter": {
                    "name": {"eq": "Widget"}
                }
            }
            """;

        MvcResult queryResult = mockMvc.perform(post("/api/gadgets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(filterJson))
                .andExpect(status().isOk())
                .andReturn();

        List<Map<String, Object>> gadgets = objectMapper.readValue(
                queryResult.getResponse().getContentAsString(),
                new TypeReference<List<Map<String, Object>>>() {});
        assertEquals(1, gadgets.size());
        assertEquals("Widget", gadgets.get(0).get("name"));
        assertEquals("blue", gadgets.get(0).get("color"));
    }

    @Test
    @Order(4)
    @DisplayName("Endpoint not present in the local config file returns 404")
    void testUnknownEndpointReturns404() throws Exception {
        mockMvc.perform(get("/api/unknown"))
                .andExpect(status().isNotFound());
    }
}
