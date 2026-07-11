package sigma.service.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import sigma.model.schema.JsonSchema;
import sigma.service.enums.EnumRegistry;
import sigma.service.enums.EnumRegistryListener;
import sigma.zookeeper.ZookeeperConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages JSON Schemas loaded from the configuration tree
 *
 * Responsibilities:
 * - Load schemas from the configuration service on demand
 * - Cache schemas in memory
 * - Provide access to schemas by name
 *
 * Configuration structure:
 * /{ENV}/{SERVICE}/schemas/
 *   ├── base-types       (common type definitions)
 *   ├── user-schema
 *   ├── product-schema
 *   └── order-schema
 */
@Service
public class SchemaManager implements EnumRegistryListener {

    private static final Logger logger = LoggerFactory.getLogger(SchemaManager.class);

    private final ZookeeperConfigService configService;
    private final String schemasBasePath;
    private final ObjectMapper objectMapper;
    private final Map<String, JsonSchema> schemaCache;
    private final EnumSchemaAugmentor enumSchemaAugmentor;

    public SchemaManager(
            ZookeeperConfigService configService,
            @Value("${zookeeper.base-path}") String basePath,
            ObjectMapper objectMapper,
            EnumRegistry enumRegistry) {
        this.configService = configService;
        this.schemasBasePath = basePath + "/schemas";
        this.objectMapper = objectMapper;
        this.schemaCache = new ConcurrentHashMap<>();
        this.enumSchemaAugmentor = new EnumSchemaAugmentor(enumRegistry);

        enumRegistry.registerListener(this);

        logger.info("SchemaManager initialized with base path: {}", schemasBasePath);
    }

    /**
     * Gets a schema by name, loading from ZooKeeper if not cached
     *
     * @param schemaName Name of the schema
     * @return The JSON schema, or null if not found
     */
    public JsonSchema getSchema(String schemaName) {
        // Check cache first
        JsonSchema cached = schemaCache.get(schemaName);
        if (cached != null) {
            logger.debug("Schema '{}' found in cache", schemaName);
            return cached;
        }

        // Load from the configuration tree
        logger.info("Loading schema '{}' from configuration", schemaName);
        return loadSchemaFromConfiguration(schemaName);
    }

    /**
     * Loads a schema from the configuration tree and caches it
     */
    private JsonSchema loadSchemaFromConfiguration(String schemaName) {
        String schemaPath = schemasBasePath + "/" + schemaName;

        String schemaJson = configService.getNodeDataAsString(schemaPath);
        if (schemaJson == null) {
            logger.warn("Schema '{}' not found in configuration at path: {}", schemaName, schemaPath);
            return null;
        }

        try {
            // Parse as JSON
            JsonNode schemaNode = objectMapper.readTree(schemaJson);
            if (!(schemaNode instanceof ObjectNode objectNode)) {
                logger.error("Schema '{}' is not a JSON object", schemaName);
                return null;
            }

            EnumSchemaAugmentor.Result result = enumSchemaAugmentor.augment(objectNode);

            // Create and cache schema
            JsonSchema schema = new JsonSchema(schemaName, result.schema(), result.bindings());
            schemaCache.put(schemaName, schema);

            logger.info("Successfully loaded schema '{}'", schemaName);
            return schema;

        } catch (IOException e) {
            logger.error("Error parsing schema '{}'", schemaName, e);
            return null;
        } catch (RuntimeException e) {
            logger.error("Error processing schema '{}'", schemaName, e);
            return null;
        }
    }

    /**
     * Clears the schema cache (useful for testing or when schemas are updated)
     */
    public void clearCache() {
        logger.info("Clearing schema cache");
        schemaCache.clear();
    }

    /**
     * Removes a specific schema from cache
     */
    public void evictSchema(String schemaName) {
        logger.info("Evicting schema '{}' from cache", schemaName);
        schemaCache.remove(schemaName);
    }

    /**
     * Returns the number of cached schemas
     */
    public int getCacheSize() {
        return schemaCache.size();
    }

    @Override
    public void onEnumsReloaded() {
        clearCache();
    }
}
