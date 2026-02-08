package sigma.integration;

import org.apache.zookeeper.ZooKeeper;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import sigma.config.properties.ZookeeperConfigProperties;
import sigma.controller.EndpointRegistry;
import sigma.model.Endpoint;
import sigma.model.filter.FilterConfig;
import sigma.model.filter.FilterOperator;
import sigma.zookeeper.ZookeeperConfigService;
import sigma.zookeeper.ZookeeperTreeReader;
import sigma.zookeeper.ZookeeperWatcher;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Test configuration that mocks ZooKeeper components for integration testing.
 * Provides in-memory configuration for endpoints without requiring a real ZooKeeper.
 */
@TestConfiguration
public class H2IntegrationTestConfig {

    // Test environment values
    private static final String TEST_ENV = "test";
    private static final String TEST_SERVICE = "dynamic-service";

    /**
     * Mock ZooKeeper client - prevents actual connection attempts
     */
    @Bean
    @Primary
    public ZooKeeper zooKeeper() {
        return Mockito.mock(ZooKeeper.class);
    }

    /**
     * Mock ZookeeperTreeReader
     */
    @Bean
    @Primary
    public ZookeeperTreeReader zookeeperTreeReader(ZooKeeper zooKeeper) {
        ZookeeperTreeReader reader = Mockito.mock(ZookeeperTreeReader.class);
        try {
            when(reader.nodeExists(anyString())).thenReturn(false);
            when(reader.readTree(anyString())).thenReturn(Map.of());
        } catch (Exception e) {
            // Ignore mock setup exceptions
        }
        return reader;
    }

    /**
     * Mock ZookeeperWatcher
     */
    @Bean
    @Primary
    public ZookeeperWatcher zookeeperWatcher(ZooKeeper zooKeeper) {
        return Mockito.mock(ZookeeperWatcher.class);
    }

    /**
     * Mock ZookeeperConfigService with test configuration
     */
    @Bean
    @Primary
    public ZookeeperConfigService zookeeperConfigService(
            ZookeeperTreeReader treeReader,
            ZookeeperWatcher watcher) {

        ZookeeperConfigService service = Mockito.mock(ZookeeperConfigService.class);

        // Create test configuration map
        Map<String, byte[]> testConfig = createTestConfiguration();

        when(service.getAllConfiguration()).thenReturn(new ConcurrentHashMap<>(testConfig));
        when(service.hasNode(anyString())).thenAnswer(inv -> testConfig.containsKey(inv.getArgument(0)));
        when(service.getNodeData(anyString())).thenAnswer(inv -> testConfig.get(inv.getArgument(0)));
        when(service.getNodeDataAsString(anyString())).thenAnswer(inv -> {
            byte[] data = testConfig.get(inv.getArgument(0));
            return data != null ? new String(data, StandardCharsets.UTF_8) : null;
        });

        return service;
    }

    /**
     * Mock ZookeeperConfigProperties
     */
    @Bean
    @Primary
    public ZookeeperConfigProperties zookeeperConfigProperties(ZookeeperConfigService configService) {
        ZookeeperConfigProperties props = Mockito.mock(ZookeeperConfigProperties.class);

        when(props.getApiPrefix()).thenReturn("/api");
        when(props.getEndpointsBasePath()).thenReturn("/" + TEST_ENV + "/" + TEST_SERVICE + "/endpoints");

        // Mock other methods that might be called
        when(props.isEnumServiceEnabled()).thenReturn(false);
        when(props.getEnumServiceUrl()).thenReturn(null);
        when(props.isEnvValidationEnabled()).thenReturn(false);
        when(props.shouldFailOnEnumLoadFailure()).thenReturn(false);
        when(props.getEnumRefreshIntervalMillis()).thenReturn(300000L);

        return props;
    }

    /**
     * Provides a pre-configured EndpointRegistry with test endpoints
     */
    @Bean
    @Primary
    public EndpointRegistry endpointRegistry(
            ZookeeperConfigService configService,
            ZookeeperConfigProperties configProperties) {

        // Create a real registry but populate it manually
        EndpointRegistry registry = new TestEndpointRegistry();

        // Register test endpoints
        registry.updateEndpoint(createProductsEndpoint());
        registry.updateEndpoint(createUsersEndpoint());

        return registry;
    }

    /**
     * Creates test configuration data simulating ZooKeeper structure
     */
    private Map<String, byte[]> createTestConfiguration() {
        String basePath = "/" + TEST_ENV + "/" + TEST_SERVICE;

        return Map.of(
            basePath + "/apiPrefix", "/api".getBytes(StandardCharsets.UTF_8),
            basePath + "/endpoints/products/path", "/products".getBytes(StandardCharsets.UTF_8),
            basePath + "/endpoints/products/httpMethod", "GET,POST,PUT,DELETE".getBytes(StandardCharsets.UTF_8),
            basePath + "/endpoints/products/databaseCollection", "products".getBytes(StandardCharsets.UTF_8),
            basePath + "/endpoints/products/type", "REST".getBytes(StandardCharsets.UTF_8),
            basePath + "/endpoints/users/path", "/users".getBytes(StandardCharsets.UTF_8),
            basePath + "/endpoints/users/httpMethod", "GET,POST".getBytes(StandardCharsets.UTF_8),
            basePath + "/endpoints/users/databaseCollection", "users".getBytes(StandardCharsets.UTF_8),
            basePath + "/endpoints/users/type", "REST".getBytes(StandardCharsets.UTF_8)
        );
    }

    /**
     * Creates a test products endpoint with full CRUD support
     */
    private Endpoint createProductsEndpoint() {
        // Read filter config: allow filtering on name, category, price
        Map<String, List<FilterOperator>> readFilterFields = Map.of(
            "name", List.of(FilterOperator.EQ, FilterOperator.REGEX),
            "category", List.of(FilterOperator.EQ, FilterOperator.IN),
            "price", List.of(FilterOperator.EQ, FilterOperator.GT, FilterOperator.GTE, FilterOperator.LT, FilterOperator.LTE)
        );
        FilterConfig readFilterConfig = new FilterConfig(readFilterFields, true);

        // Write filter config: allow filtering on id only for updates/deletes
        Map<String, List<FilterOperator>> writeFilterFields = Map.of(
            "id", List.of(FilterOperator.EQ)
        );
        FilterConfig writeFilterConfig = new FilterConfig(writeFilterFields, true);

        return new Endpoint(
            "products",
            "/products",
            "GET,POST,PUT,DELETE",
            "products",
            Endpoint.EndpointType.REST,
            true,  // sequenceEnabled
            100,   // defaultBulkSize
            readFilterConfig,
            writeFilterConfig,
            null,  // schemaReference
            Set.of("POST", "PUT", "DELETE"),  // allowedWriteMethods
            Set.of(),  // subEntities
            null   // fatherDocument
        );
    }

    /**
     * Creates a test users endpoint with read and create support
     */
    private Endpoint createUsersEndpoint() {
        Map<String, List<FilterOperator>> readFilterFields = Map.of(
            "username", List.of(FilterOperator.EQ, FilterOperator.REGEX),
            "email", List.of(FilterOperator.EQ),
            "status", List.of(FilterOperator.EQ, FilterOperator.IN)
        );
        FilterConfig readFilterConfig = new FilterConfig(readFilterFields, true);

        Map<String, List<FilterOperator>> writeFilterFields = Map.of(
            "id", List.of(FilterOperator.EQ)
        );
        FilterConfig writeFilterConfig = new FilterConfig(writeFilterFields, true);

        return new Endpoint(
            "users",
            "/users",
            "GET,POST",
            "users",
            Endpoint.EndpointType.REST,
            false, // sequenceEnabled
            50,    // defaultBulkSize
            readFilterConfig,
            writeFilterConfig,
            null,
            Set.of("POST"),
            Set.of(),
            null
        );
    }

    /**
     * Custom EndpointRegistry that doesn't require ZooKeeper initialization
     */
    private static class TestEndpointRegistry extends EndpointRegistry {

        private final Map<String, Endpoint> endpoints = new ConcurrentHashMap<>();

        public TestEndpointRegistry() {
            super(null, null); // Pass nulls - we override all methods
        }

        @Override
        public void loadEndpoints() {
            // Do nothing - endpoints are added manually
        }

        @Override
        public Endpoint findEndpoint(String path, String httpMethod) {
            String cacheKey = httpMethod.toUpperCase() + ":" + path;
            return endpoints.get(cacheKey);
        }

        @Override
        public Map<String, Endpoint> getAllEndpoints() {
            return new ConcurrentHashMap<>(endpoints);
        }

        @Override
        public void updateEndpoint(Endpoint endpoint) {
            String cacheKey = endpoint.getCacheKey();
            endpoints.put(cacheKey, endpoint);
        }

        @Override
        public void removeEndpoint(String path, String httpMethod) {
            String cacheKey = httpMethod.toUpperCase() + ":" + path;
            endpoints.remove(cacheKey);
        }
    }
}
