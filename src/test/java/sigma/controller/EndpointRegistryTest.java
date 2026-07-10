package sigma.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sigma.config.properties.ZookeeperConfigProperties;
import sigma.model.Endpoint;
import sigma.zookeeper.ZookeeperConfigService;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class EndpointRegistryTest {

    @Mock
    private ZookeeperConfigService configService;

    @Mock
    private ZookeeperConfigProperties configProperties;

    private EndpointRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new EndpointRegistry(configService, configProperties);
    }

    private Endpoint endpoint(String path, String httpMethod) {
        return new Endpoint(
                "test", path, httpMethod, "test",
                Endpoint.EndpointType.REST, false, 100,
                null, null, null, Set.of(), Set.of(), null);
    }

    @Test
    @DisplayName("Multi-method endpoint is findable by each individual HTTP method")
    void testFindEndpointForEachMethod() {
        registry.updateEndpoint(endpoint("/products", "GET,POST,PUT,DELETE"));

        assertNotNull(registry.findEndpoint("/products", "GET"));
        assertNotNull(registry.findEndpoint("/products", "POST"));
        assertNotNull(registry.findEndpoint("/products", "PUT"));
        assertNotNull(registry.findEndpoint("/products", "DELETE"));
        assertNull(registry.findEndpoint("/products", "PATCH"));
        assertNull(registry.findEndpoint("/other", "GET"));
    }

    @Test
    @DisplayName("Lookup is case-insensitive on HTTP method")
    void testFindEndpointCaseInsensitive() {
        registry.updateEndpoint(endpoint("/users", "GET"));

        assertNotNull(registry.findEndpoint("/users", "get"));
    }

    @Test
    @DisplayName("Removing a multi-method endpoint clears every method entry")
    void testRemoveMultiMethodEndpoint() {
        registry.updateEndpoint(endpoint("/products", "GET,POST"));

        registry.removeEndpoint("/products", "GET,POST");

        assertNull(registry.findEndpoint("/products", "GET"));
        assertNull(registry.findEndpoint("/products", "POST"));
    }

    @Test
    @DisplayName("Removing a single method keeps the other method entries")
    void testRemoveSingleMethod() {
        registry.updateEndpoint(endpoint("/products", "GET,POST"));

        registry.removeEndpoint("/products", "POST");

        assertNotNull(registry.findEndpoint("/products", "GET"));
        assertNull(registry.findEndpoint("/products", "POST"));
    }
}
