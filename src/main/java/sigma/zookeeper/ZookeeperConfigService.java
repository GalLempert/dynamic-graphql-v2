package sigma.zookeeper;

import jakarta.annotation.PostConstruct;
import org.apache.zookeeper.KeeperException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ZookeeperConfigService {

    private static final Logger logger = LoggerFactory.getLogger(ZookeeperConfigService.class);

    private final ZookeeperTreeReader treeReader;
    private final ZookeeperWatcher watcher;
    private final Map<String, byte[]> configuration = new ConcurrentHashMap<>();

    private final String env;
    private final String service;
    private final String serviceRootPath;
    private final String dataSourceRootPath;
    private final String globalsRootPath;

    public ZookeeperConfigService(ZookeeperTreeReader treeReader, ZookeeperWatcher watcher) {
        this.treeReader = treeReader;
        this.watcher = watcher;

        // Read environment variables
        this.env = System.getenv("ENV");
        this.service = System.getenv("SERVICE");

        if (env == null || env.isEmpty()) {
            throw new IllegalStateException("ENV environment variable is required");
        }
        if (service == null || service.isEmpty()) {
            throw new IllegalStateException("SERVICE environment variable is required");
        }

        // Build paths
        this.serviceRootPath = "/" + env + "/" + service;
        this.dataSourceRootPath = "/" + env + "/dataSource";
        this.globalsRootPath = "/" + env + "/Globals";

        logger.info("Zookeeper paths initialized - Service: {}, DataSource: {}", serviceRootPath, dataSourceRootPath);
    }

    /**
     * Initializes the configuration by reading the entire Zookeeper tree
     * and setting up watchers on all nodes
     */
    @PostConstruct
    public void initialize() {
        try {
            logger.info("Initializing Zookeeper configuration for ENV={}, SERVICE={}", env, service);

            // Load service configuration
            loadTreeIfExists(serviceRootPath, "Service");

            // Load dataSource configuration
            loadTreeIfExists(dataSourceRootPath, "DataSource");

            // Load globals configuration
            loadTreeIfExists(globalsRootPath, "Globals");

            logger.info("Total configuration nodes loaded: {}", configuration.size());

        } catch (KeeperException | InterruptedException e) {
            logger.error("Failed to initialize Zookeeper configuration", e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void loadTreeIfExists(String rootPath, String treeName) throws KeeperException, InterruptedException {
        if (!treeReader.nodeExists(rootPath)) {
            logger.warn("{} tree {} does not exist in Zookeeper", treeName, rootPath);
            return;
        }

        logger.info("Loading {} configuration from: {}", treeName, rootPath);

        // Read the entire tree
        Map<String, byte[]> tree = treeReader.readTree(rootPath);
        configuration.putAll(tree);

        logger.info("Loaded {} nodes from {} tree", tree.size(), treeName);

        // Log all loaded configurations
        tree.forEach((path, data) -> {
            String value = data != null ? new String(data, StandardCharsets.UTF_8) : "null";
            logger.debug("Config loaded: {} = {}", path, value);
        });

        // Set up watchers on the entire tree
        watcher.watchTree(rootPath);
        logger.info("Watchers set up on {} tree", treeName);
    }

    /**
     * Updates a node in the configuration
     */
    public void updateNode(String path, byte[] data) {
        configuration.put(path, data);
        String value = data != null ? new String(data, StandardCharsets.UTF_8) : "null";
        logger.info("Configuration updated: {} = {}", path, value);
    }

    /**
     * Removes a node from the configuration
     */
    public void removeNode(String path) {
        configuration.remove(path);
        logger.info("Configuration removed: {}", path);
    }

    /**
     * Gets the value of a configuration node
     */
    public byte[] getNodeData(String path) {
        return configuration.get(path);
    }

    /**
     * Gets the value of a configuration node as a String
     */
    public String getNodeDataAsString(String path) {
        byte[] data = configuration.get(path);
        return data != null ? new String(data, StandardCharsets.UTF_8) : null;
    }

    /**
     * Gets all configuration nodes
     */
    public Map<String, byte[]> getAllConfiguration() {
        return new ConcurrentHashMap<>(configuration);
    }

    /**
     * Checks if a configuration node exists
     */
    public boolean hasNode(String path) {
        return configuration.containsKey(path);
    }
}
