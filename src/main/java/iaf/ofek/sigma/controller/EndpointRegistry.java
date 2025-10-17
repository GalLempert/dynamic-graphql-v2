package iaf.ofek.sigma.controller;

import iaf.ofek.sigma.config.properties.ZookeeperConfigProperties;
import iaf.ofek.sigma.model.Endpoint;
import iaf.ofek.sigma.zookeeper.ZookeeperConfigService;
import iaf.ofek.sigma.zookeeper.util.ZookeeperUtils;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry that caches dynamic endpoints from Zookeeper
 * Maintains a HashMap of endpoints for fast lookup
 */
@Component
public class EndpointRegistry {

    private static final Logger logger = LoggerFactory.getLogger(EndpointRegistry.class);

    private final ZookeeperConfigService configService;
    private final ZookeeperConfigProperties configProperties;
    private final Map<String, Endpoint> endpointCache = new ConcurrentHashMap<>();

    public EndpointRegistry(ZookeeperConfigService configService,
                           ZookeeperConfigProperties configProperties) {
        this.configService = configService;
        this.configProperties = configProperties;
    }

    /**
     * Loads all endpoints from Zookeeper on startup
     * Structure: /{ENV}/{SERVICE}/endpoints/{endpointName}/{path,httpMethod,databaseCollection,type}
     */
    @PostConstruct
    public void loadEndpoints() {
        logger.info("Loading endpoints from Zookeeper");

        String endpointsBasePath = configProperties.getEndpointsBasePath();
        Map<String, byte[]> allConfig = configService.getAllConfiguration();

        // Group endpoint data by endpoint name
        Map<String, Map<String, String>> endpointData = new HashMap<>();

        allConfig.forEach((path, data) -> {
            if (path.startsWith(endpointsBasePath + "/")) {
                String relativePath = path.substring(endpointsBasePath.length() + 1);
                String[] parts = relativePath.split("/", 2);

                if (parts.length == 2) {
                    String endpointName = parts[0];
                    String propertyName = parts[1];
                    String value = ZookeeperUtils.bytesToString(data).orElse(null);

                    endpointData.computeIfAbsent(endpointName, k -> new HashMap<>())
                               .put(propertyName, value);
                }
            }
        });

        // Build Endpoint objects
        endpointData.forEach((name, properties) -> {
            try {
                String path = properties.get("path");
                String httpMethod = properties.get("httpMethod");
                String databaseCollection = properties.get("databaseCollection");
                String type = properties.get("type");
                String sequenceEnabledStr = properties.get("sequenceEnabled");
                String defaultBulkSizeStr = properties.get("defaultBulkSize");

                if (path == null || httpMethod == null || databaseCollection == null) {
                    logger.warn("Incomplete endpoint configuration for: {}. Skipping.", name);
                    return;
                }

                boolean sequenceEnabled = Boolean.parseBoolean(sequenceEnabledStr);
                int defaultBulkSize = defaultBulkSizeStr != null ? Integer.parseInt(defaultBulkSizeStr) : 100;

                Endpoint endpoint = new Endpoint(
                    name,
                    path,
                    httpMethod,
                    databaseCollection,
                    Endpoint.EndpointType.fromString(type),
                    sequenceEnabled,
                    defaultBulkSize
                );

                String cacheKey = endpoint.getCacheKey();
                endpointCache.put(cacheKey, endpoint);
                logger.info("Registered endpoint: {} -> {}", cacheKey, endpoint);

            } catch (Exception e) {
                logger.error("Failed to create endpoint for: {}", name, e);
            }
        });

        logger.info("Loaded {} endpoints from Zookeeper", endpointCache.size());
    }

    /**
     * Finds an endpoint by path and HTTP method
     */
    public Endpoint findEndpoint(String path, String httpMethod) {
        String cacheKey = httpMethod.toUpperCase() + ":" + path;
        return endpointCache.get(cacheKey);
    }

    /**
     * Gets all registered endpoints
     */
    public Map<String, Endpoint> getAllEndpoints() {
        return new HashMap<>(endpointCache);
    }

    /**
     * Updates or adds an endpoint to the cache
     * Called when Zookeeper configuration changes
     */
    public void updateEndpoint(Endpoint endpoint) {
        String cacheKey = endpoint.getCacheKey();
        endpointCache.put(cacheKey, endpoint);
        logger.info("Updated endpoint: {} -> {}", cacheKey, endpoint);
    }

    /**
     * Removes an endpoint from the cache
     */
    public void removeEndpoint(String path, String httpMethod) {
        String cacheKey = httpMethod.toUpperCase() + ":" + path;
        Endpoint removed = endpointCache.remove(cacheKey);
        if (removed != null) {
            logger.info("Removed endpoint: {}", cacheKey);
        }
    }
}
