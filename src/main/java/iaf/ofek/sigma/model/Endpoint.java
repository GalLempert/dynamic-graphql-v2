package iaf.ofek.sigma.model;

import iaf.ofek.sigma.model.filter.FilterConfig;

/**
 * Represents a dynamic endpoint configuration from Zookeeper
 * Structure in Zookeeper: /{ENV}/{SERVICE}/endpoints/{endpointName}/
 */
public class Endpoint {

    private final String name;
    private final String path;
    private final String httpMethod;
    private final String databaseCollection;
    private final EndpointType type;
    private final boolean sequenceEnabled;
    private final int defaultBulkSize;
    private final FilterConfig filterConfig;

    public Endpoint(String name, String path, String httpMethod, String databaseCollection,
                   EndpointType type, boolean sequenceEnabled, int defaultBulkSize, FilterConfig filterConfig) {
        this.name = name;
        this.path = path;
        this.httpMethod = httpMethod;
        this.databaseCollection = databaseCollection;
        this.type = type;
        this.sequenceEnabled = sequenceEnabled;
        this.defaultBulkSize = defaultBulkSize;
        this.filterConfig = filterConfig;
    }

    public String getName() {
        return name;
    }

    public String getPath() {
        return path;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public String getDatabaseCollection() {
        return databaseCollection;
    }

    public EndpointType getType() {
        return type;
    }

    public boolean isSequenceEnabled() {
        return sequenceEnabled;
    }

    public int getDefaultBulkSize() {
        return defaultBulkSize;
    }

    public FilterConfig getFilterConfig() {
        return filterConfig;
    }

    /**
     * Creates a cache key for this endpoint (path + method)
     */
    public String getCacheKey() {
        return httpMethod.toUpperCase() + ":" + path;
    }

    @Override
    public String toString() {
        return "Endpoint{" +
                "name='" + name + '\'' +
                ", path='" + path + '\'' +
                ", httpMethod='" + httpMethod + '\'' +
                ", databaseCollection='" + databaseCollection + '\'' +
                ", type=" + type +
                ", sequenceEnabled=" + sequenceEnabled +
                ", defaultBulkSize=" + defaultBulkSize +
                ", filterConfig=" + filterConfig +
                '}';
    }

    public enum EndpointType {
        REST,
        GRAPHQL;

        public static EndpointType fromString(String type) {
            if (type == null) {
                return REST;
            }
            return switch (type.toUpperCase()) {
                case "GRAPHQL" -> GRAPHQL;
                case "REST" -> REST;
                default -> REST;
            };
        }
    }
}
