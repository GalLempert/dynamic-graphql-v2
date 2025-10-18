package iaf.ofek.sigma.model;

import iaf.ofek.sigma.model.filter.FilterConfig;
import iaf.ofek.sigma.model.schema.SchemaReference;

import java.util.Set;

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
    private final SchemaReference schemaReference;
    private final Set<String> allowedWriteMethods;

    public Endpoint(String name, String path, String httpMethod, String databaseCollection,
                   EndpointType type, boolean sequenceEnabled, int defaultBulkSize, FilterConfig filterConfig,
                   SchemaReference schemaReference, Set<String> allowedWriteMethods) {
        this.name = name;
        this.path = path;
        this.httpMethod = httpMethod;
        this.databaseCollection = databaseCollection;
        this.type = type;
        this.sequenceEnabled = sequenceEnabled;
        this.defaultBulkSize = defaultBulkSize;
        this.filterConfig = filterConfig;
        this.schemaReference = schemaReference;
        this.allowedWriteMethods = allowedWriteMethods != null ? allowedWriteMethods : Set.of();
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

    public SchemaReference getSchemaReference() {
        return schemaReference;
    }

    public Set<String> getAllowedWriteMethods() {
        return allowedWriteMethods;
    }

    /**
     * Checks if a specific HTTP method is allowed for write operations
     */
    public boolean isWriteMethodAllowed(String method) {
        return allowedWriteMethods.contains(method.toUpperCase());
    }

    /**
     * Checks if this endpoint requires schema validation for writes
     */
    public boolean requiresSchemaValidation() {
        return schemaReference != null && schemaReference.isRequired();
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
                ", schemaReference=" + schemaReference +
                ", allowedWriteMethods=" + allowedWriteMethods +
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
