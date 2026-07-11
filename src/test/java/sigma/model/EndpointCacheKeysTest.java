package sigma.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class EndpointCacheKeysTest {

    private Endpoint endpoint(String httpMethod) {
        return new Endpoint(
                "products", "/products", httpMethod, "products",
                Endpoint.EndpointType.REST, false, 100,
                null, null, null, Set.of(), Set.of(), null);
    }

    @Test
    @DisplayName("Single-method endpoint produces one cache key")
    void testSingleMethod() {
        assertEquals(Set.of("GET:/products"), endpoint("GET").getCacheKeys());
    }

    @Test
    @DisplayName("Comma-separated methods produce one cache key per method")
    void testMultipleMethods() {
        assertEquals(
                Set.of("GET:/products", "POST:/products", "PUT:/products", "DELETE:/products"),
                endpoint("GET,POST,PUT,DELETE").getCacheKeys());
    }

    @Test
    @DisplayName("Methods are trimmed and upper-cased")
    void testNormalization() {
        assertEquals(
                Set.of("GET:/products", "POST:/products"),
                endpoint(" get , post ").getCacheKeys());
    }
}
