package sigma.console.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import sigma.model.Endpoint;
import sigma.model.filter.FilterConfig;
import sigma.model.filter.FilterOperator;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ResourceCompilerTest {

    private ResourceDefinition definition() {
        ResourceDefinition def = new ResourceDefinition();
        def.setName("orders");
        def.setDescription("Customer orders");
        def.getActions().setRead(true);
        def.getActions().setAdd(true);
        def.getActions().setChange(true);
        def.setSearch(Map.of(
                "status", List.of("equals", "one of"),
                "total", List.of("at least", "at most")));
        def.setChangeBy(Map.of("id", List.of("equals")));
        def.getFeatures().setChangeFeed(true);
        def.getFeatures().setPageSize(50);
        return def;
    }

    private String value(Map<String, byte[]> nodes, String path) {
        byte[] data = nodes.get(path);
        return data == null ? null : new String(data, StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("Definition compiles to the engine's endpoint configuration tree")
    void testCompile() {
        Map<String, byte[]> nodes = ResourceCompiler.compile(definition(), "/test/svc/endpoints");

        assertEquals("/orders", value(nodes, "/test/svc/endpoints/orders/path"));
        assertEquals("GET,POST,PUT", value(nodes, "/test/svc/endpoints/orders/httpMethod"));
        assertEquals("orders", value(nodes, "/test/svc/endpoints/orders/databaseCollection"));
        assertEquals("REST", value(nodes, "/test/svc/endpoints/orders/type"));
        assertEquals("POST,PUT", value(nodes, "/test/svc/endpoints/orders/writeMethods"));
        assertEquals("true", value(nodes, "/test/svc/endpoints/orders/sequenceEnabled"));
        assertEquals("50", value(nodes, "/test/svc/endpoints/orders/defaultBulkSize"));
        assertEquals("Customer orders", value(nodes, "/test/svc/endpoints/orders/description"));
        assertEquals("eq,in", value(nodes, "/test/svc/endpoints/orders/readFilter/status"));
        assertEquals("gte,lte", value(nodes, "/test/svc/endpoints/orders/readFilter/total"));
        assertEquals("eq", value(nodes, "/test/svc/endpoints/orders/writeFilter/id"));
    }

    @Test
    @DisplayName("Read-only resource still routes POST for body search but allows no writes")
    void testCompileReadOnly() {
        ResourceDefinition def = new ResourceDefinition();
        def.setName("reports");
        def.getActions().setRead(true);

        Map<String, byte[]> nodes = ResourceCompiler.compile(def, "/b/e");
        assertEquals("GET,POST", value(nodes, "/b/e/reports/httpMethod"));
        assertNull(value(nodes, "/b/e/reports/writeMethods"));
    }

    @Test
    @DisplayName("Validation speaks product language")
    void testValidation() {
        ResourceDefinition def = new ResourceDefinition();
        def.setName("Bad Name!");
        def.getActions().setRead(false);
        def.setSearch(Map.of("price", List.of("bigger than")));
        def.getFeatures().setPageSize(0);

        List<String> problems = ResourceCompiler.validate(def);

        assertTrue(problems.stream().anyMatch(p -> p.contains("lowercase")), "name problem expected");
        assertTrue(problems.stream().anyMatch(p -> p.contains("at least one action")), "action problem expected");
        assertTrue(problems.stream().anyMatch(p -> p.contains("Unknown condition 'bigger than'")), "condition problem expected");
        assertTrue(problems.stream().anyMatch(p -> p.contains("Page size")), "page size problem expected");
        // no engine symbols leak into messages
        assertTrue(problems.stream().noneMatch(p -> p.contains("regex") || p.contains("gte")),
                "problems must not use engine vocabulary");
    }

    @Test
    @DisplayName("Valid definition has no problems")
    void testValidationPasses() {
        assertTrue(ResourceCompiler.validate(definition()).isEmpty());
    }

    @Test
    @DisplayName("Live endpoint converts back to a product-language definition")
    void testToDefinition() {
        Endpoint endpoint = new Endpoint(
                "products", "/products", "GET,POST,PUT,DELETE", "products",
                Endpoint.EndpointType.REST, true, 100,
                new FilterConfig(Map.of("price", List.of(FilterOperator.GTE, FilterOperator.REGEX)), true),
                new FilterConfig(Map.of("id", List.of(FilterOperator.EQ)), true),
                null, Set.of("POST", "PUT", "DELETE"), Set.of(), null);

        ResourceDefinition def = ResourceCompiler.toDefinition(endpoint, "All products");

        assertEquals("products", def.getName());
        assertEquals("All products", def.getDescription());
        assertTrue(def.getActions().isRead());
        assertTrue(def.getActions().isAdd());
        assertTrue(def.getActions().isChange());
        assertTrue(def.getActions().isRemove());
        assertEquals(List.of("at least", "matches pattern"), def.getSearch().get("price"));
        assertEquals(List.of("equals"), def.getChangeBy().get("id"));
        assertTrue(def.getFeatures().isChangeFeed());
    }
}
