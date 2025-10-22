package sigma.service.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import sigma.model.schema.JsonSchema;
import sigma.service.enums.EnumRegistry;
import sigma.service.enums.EnumRegistryListener;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooKeeper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages JSON Schemas loaded from ZooKeeper
 *
 * Responsibilities:
 * - Load schemas from ZooKeeper on demand
 * - Cache schemas in memory
 * - Provide access to schemas by name
 *
 * ZooKeeper Structure:
 * /{ENV}/{SERVICE}/schemas/
 *   ├── base-types       (common type definitions)
 *   ├── user-schema
 *   ├── product-schema
 *   └── order-schema
 */
@Service
public class SchemaManager implements EnumRegistryListener {

    private static final Logger logger = LoggerFactory.getLogger(SchemaManager.class);

    private final ZooKeeper zooKeeper;
    private final String schemasBasePath;
    private final ObjectMapper objectMapper;
    private final Map<String, JsonSchema> schemaCache;
    private final EnumSchemaAugmentor enumSchemaAugmentor;

    public SchemaManager(
            ZooKeeper zooKeeper,
            @Value("${zookeeper.base-path}") String basePath,
            ObjectMapper objectMapper,
            EnumRegistry enumRegistry) {
        this.zooKeeper = zooKeeper;
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

        // Load from ZooKeeper
        logger.info("Loading schema '{}' from ZooKeeper", schemaName);
        return loadSchemaFromZooKeeper(schemaName);
    }

    /**
     * Loads a schema from ZooKeeper and caches it
     */
    private JsonSchema loadSchemaFromZooKeeper(String schemaName) {
        String schemaPath = schemasBasePath + "/" + schemaName;

        try {
            // Get schema data from ZooKeeper
            byte[] data = zooKeeper.getData(schemaPath, false, null);
            String schemaJson = new String(data);

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

            logger.info("Successfully loaded schema '{}' from ZooKeeper", schemaName);
            return schema;

        } catch (KeeperException.NoNodeException e) {
            logger.warn("Schema '{}' not found in ZooKeeper at path: {}", schemaName, schemaPath);
            return null;

        } catch (KeeperException | InterruptedException | IOException e) {
            logger.error("Error loading schema '{}' from ZooKeeper", schemaName, e);
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
