package iaf.ofek.sigma.config.properties;

import iaf.ofek.sigma.zookeeper.ZookeeperConfigService;
import iaf.ofek.sigma.zookeeper.util.ZookeeperUtils;
import org.springframework.stereotype.Component;

/**
 * Provides concrete access to specific Zookeeper configuration leaves
 * Add specific getter methods for each configuration property your service needs
 *
 * Example usage:
 * - getMongoHost() -> reads from /{ENV}/dataSource/mongodb.host
 * - getServiceTimeout() -> reads from /{ENV}/{SERVICE}/timeout
 */
@Component
public class ZookeeperConfigProperties {

    private final ZookeeperConfigService configService;
    private final String serviceBasePath;
    private final String dataSourceBasePath;

    public ZookeeperConfigProperties(ZookeeperConfigService configService) {
        this.configService = configService;
        String env = System.getenv("ENV");
        String service = System.getenv("SERVICE");

        if (env == null || env.isEmpty()) {
            throw new IllegalStateException("ENV environment variable is required");
        }
        if (service == null || service.isEmpty()) {
            throw new IllegalStateException("SERVICE environment variable is required");
        }

        this.serviceBasePath = "/" + env + "/" + service;
        this.dataSourceBasePath = "/" + env + "/dataSource";
    }

    // ========== Service Configuration ==========

    /**
     * Gets the API prefix for this service
     * Example: /api/v1
     */
    public String getApiPrefix() {
        return getServiceProperty("apiPrefix", "/api");
    }

    /**
     * Gets the endpoints base path in Zookeeper
     */
    public String getEndpointsBasePath() {
        return serviceBasePath + "/endpoints";
    }

    // ========== MongoDB DataSource Configuration ==========

    public String getMongoHost() {
        return getDataSourceProperty("mongodb.host", "localhost");
    }

    public String getMongoPort() {
        return getDataSourceProperty("mongodb.port", "27017");
    }

    public String getMongoDatabase() {
        return getDataSourceProperty("mongodb.database", "dynamic-graphql");
    }

    public String getMongoUsername() {
        return getDataSourceProperty("mongodb.username");
    }

    public String getMongoPassword() {
        return getDataSourceProperty("mongodb.password");
    }

    public String getMongoAuthDatabase() {
        return getDataSourceProperty("mongodb.authDatabase", "admin");
    }

    // ========== Helper Methods ==========

    /**
     * Gets a DataSource property as String with default value
     */
    public String getDataSourceProperty(String key, String defaultValue) {
        String fullPath = dataSourceBasePath + "/" + key;
        byte[] data = configService.getNodeData(fullPath);
        return ZookeeperUtils.bytesToString(data, defaultValue);
    }

    /**
     * Gets a DataSource property as String (may be null)
     */
    public String getDataSourceProperty(String key) {
        String fullPath = dataSourceBasePath + "/" + key;
        byte[] data = configService.getNodeData(fullPath);
        return ZookeeperUtils.bytesToString(data).orElse(null);
    }

    /**
     * Gets a service-specific property as String with default value
     */
    public String getServiceProperty(String key, String defaultValue) {
        String fullPath = serviceBasePath + "/" + key;
        byte[] data = configService.getNodeData(fullPath);
        return ZookeeperUtils.bytesToString(data, defaultValue);
    }

    /**
     * Gets a service-specific property as String (may be null)
     */
    public String getServiceProperty(String key) {
        String fullPath = serviceBasePath + "/" + key;
        byte[] data = configService.getNodeData(fullPath);
        return ZookeeperUtils.bytesToString(data).orElse(null);
    }

    /**
     * Gets a service-specific property as Integer with default value
     */
    public Integer getServicePropertyAsInt(String key, Integer defaultValue) {
        String fullPath = serviceBasePath + "/" + key;
        byte[] data = configService.getNodeData(fullPath);
        return ZookeeperUtils.bytesToInteger(data, defaultValue);
    }

    /**
     * Gets a service-specific property as Boolean with default value
     */
    public Boolean getServicePropertyAsBoolean(String key, Boolean defaultValue) {
        String fullPath = serviceBasePath + "/" + key;
        byte[] data = configService.getNodeData(fullPath);
        return ZookeeperUtils.bytesToBoolean(data, defaultValue);
    }
}
