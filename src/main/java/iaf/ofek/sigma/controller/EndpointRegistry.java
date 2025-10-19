package iaf.ofek.sigma.controller;

import iaf.ofek.sigma.config.properties.ZookeeperConfigProperties;
import iaf.ofek.sigma.model.Endpoint;
import iaf.ofek.sigma.model.filter.FilterConfig;
import iaf.ofek.sigma.model.filter.FilterOperator;
import iaf.ofek.sigma.model.schema.SchemaReference;
import iaf.ofek.sigma.zookeeper.ZookeeperConfigService;
import iaf.ofek.sigma.zookeeper.util.ZookeeperUtils;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

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

                // Load separate filter configurations for read and write operations
                FilterConfig readFilterConfig = loadFilterConfig(name, endpointsBasePath, "readFilter");
                FilterConfig writeFilterConfig = loadFilterConfig(name, endpointsBasePath, "writeFilter");

                // Load schema reference
                SchemaReference schemaReference = loadSchemaReference(name, endpointsBasePath);

                // Load allowed write methods
                Set<String> allowedWriteMethods = loadAllowedWriteMethods(name, endpointsBasePath);

                // Load configured sub-entity fields
                Set<String> subEntities = loadSubEntities(name, endpointsBasePath);

                Endpoint endpoint = new Endpoint(
                    name,
                    path,
                    httpMethod,
                    databaseCollection,
                    Endpoint.EndpointType.fromString(type),
                    sequenceEnabled,
                    defaultBulkSize,
                    readFilterConfig,
                    writeFilterConfig,
                    schemaReference,
                    allowedWriteMethods,
                    subEntities
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

    /**
     * Loads filter configuration for an endpoint from Zookeeper
     * Structure: /{ENV}/{SERVICE}/endpoints/{endpointName}/{filterType}/{fieldName}
     * Each field contains comma-separated operators: $eq,$gt,$lt
     * 
     * @param endpointName Name of the endpoint
     * @param endpointsBasePath Base path for endpoints in ZooKeeper
     * @param filterType "readFilter" or "writeFilter"
     */
    private FilterConfig loadFilterConfig(String endpointName, String endpointsBasePath, String filterType) {
        String filterBasePath = endpointsBasePath + "/" + endpointName + "/" + filterType;
        Map<String, byte[]> allConfig = configService.getAllConfiguration();
        Map<String, List<FilterOperator>> fieldOperators = new HashMap<>();
        boolean filterEnabled = false;

        // Look for filter configuration nodes
        for (Map.Entry<String, byte[]> entry : allConfig.entrySet()) {
            String path = entry.getKey();
            if (path.startsWith(filterBasePath + "/")) {
                filterEnabled = true;
                String fieldName = path.substring(filterBasePath.length() + 1);
                String operatorsStr = ZookeeperUtils.bytesToString(entry.getValue()).orElse("");

                // Parse operators (comma-separated)
                List<FilterOperator> operators = Arrays.stream(operatorsStr.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .map(FilterOperator::fromString)
                        .collect(Collectors.toList());

                fieldOperators.put(fieldName, operators);
                logger.debug("Loaded filter config for {}.{}: {}", endpointName, fieldName, operators);
            }
        }

        FilterConfig config = new FilterConfig(fieldOperators, filterEnabled);
        if (filterEnabled) {
            logger.info("Filter enabled for endpoint: {} with {} filterable fields", endpointName, fieldOperators.size());
        }
        return config;
    }

    /**
     * Loads schema reference for an endpoint from Zookeeper
     * Structure: /{ENV}/{SERVICE}/endpoints/{endpointName}/schema
     * Value format: "schemaName" or "schemaName:required"
     */
    private SchemaReference loadSchemaReference(String endpointName, String endpointsBasePath) {
        String schemaPath = endpointsBasePath + "/" + endpointName + "/schema";
        Map<String, byte[]> allConfig = configService.getAllConfiguration();

        byte[] schemaData = allConfig.get(schemaPath);
        if (schemaData == null) {
            return null; // No schema configured
        }

        String schemaValue = ZookeeperUtils.bytesToString(schemaData).orElse("");
        if (schemaValue.isEmpty()) {
            return null;
        }

        // Parse format: "schemaName" or "schemaName:required"
        String[] parts = schemaValue.split(":");
        String schemaName = parts[0].trim();
        boolean required = parts.length > 1 && "required".equalsIgnoreCase(parts[1].trim());

        SchemaReference schemaReference = new SchemaReference(schemaName, required);
        logger.info("Loaded schema reference for endpoint {}: {}", endpointName, schemaReference);
        return schemaReference;
    }

    /**
     * Loads allowed write methods for an endpoint from Zookeeper
     * Structure: /{ENV}/{SERVICE}/endpoints/{endpointName}/writeMethods
     * Value format: comma-separated HTTP methods: "POST,PUT,PATCH,DELETE"
     */
    private Set<String> loadAllowedWriteMethods(String endpointName, String endpointsBasePath) {
        String writeMethodsPath = endpointsBasePath + "/" + endpointName + "/writeMethods";
        Map<String, byte[]> allConfig = configService.getAllConfiguration();

        byte[] writeMethodsData = allConfig.get(writeMethodsPath);
        if (writeMethodsData == null) {
            return Set.of(); // No write methods configured
        }

        String writeMethodsValue = ZookeeperUtils.bytesToString(writeMethodsData).orElse("");
        if (writeMethodsValue.isEmpty()) {
            return Set.of();
        }

        // Parse comma-separated methods
        Set<String> methods = Arrays.stream(writeMethodsValue.split(","))
                .map(String::trim)
                .map(String::toUpperCase)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());

        logger.info("Loaded write methods for endpoint {}: {}", endpointName, methods);
        return methods;
    }

    private Set<String> loadSubEntities(String endpointName, String endpointsBasePath) {
        String subEntitiesPath = endpointsBasePath + "/" + endpointName + "/subEntities";
        Map<String, byte[]> allConfig = configService.getAllConfiguration();

        byte[] subEntitiesData = allConfig.get(subEntitiesPath);
        if (subEntitiesData == null) {
            return Set.of();
        }

        String value = ZookeeperUtils.bytesToString(subEntitiesData).orElse("");
        if (value.isEmpty()) {
            return Set.of();
        }

        Set<String> fields = Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(() -> new java.util.LinkedHashSet<>()));

        logger.info("Loaded sub-entities for endpoint {}: {}", endpointName, fields);
        return fields;
    }
}
