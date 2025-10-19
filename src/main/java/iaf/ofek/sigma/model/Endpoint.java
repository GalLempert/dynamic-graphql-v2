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
    private final FilterConfig readFilterConfig;
    private final FilterConfig writeFilterConfig;
    private final SchemaReference schemaReference;
    private final Set<String> allowedWriteMethods;
    private final Set<String> subEntities;

    public Endpoint(String name, String path, String httpMethod, String databaseCollection,
                   EndpointType type, boolean sequenceEnabled, int defaultBulkSize,
                   FilterConfig readFilterConfig, FilterConfig writeFilterConfig,
                   SchemaReference schemaReference, Set<String> allowedWriteMethods,
                   Set<String> subEntities) {
        this.name = name;
        this.path = path;
        this.httpMethod = httpMethod;
        this.databaseCollection = databaseCollection;
        this.type = type;
        this.sequenceEnabled = sequenceEnabled;
        this.defaultBulkSize = defaultBulkSize;
        this.readFilterConfig = readFilterConfig;
        this.writeFilterConfig = writeFilterConfig;
        this.schemaReference = schemaReference;
        this.allowedWriteMethods = allowedWriteMethods != null ? Set.copyOf(allowedWriteMethods) : Set.of();
        this.subEntities = subEntities != null ? Set.copyOf(subEntities) : Set.of();
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

    /**
     * Gets the filter configuration for READ operations
     * Read filters can be more permissive (allowing complex queries with $or, $in, etc.)
     */
    public FilterConfig getReadFilterConfig() {
        return readFilterConfig;
    }

    /**
     * Gets the filter configuration for WRITE operations
     * Write filters should be more restrictive to prevent mass updates/deletes
     * Typically limited to _id and unique identifier fields
     */
    public FilterConfig getWriteFilterConfig() {
        return writeFilterConfig;
    }

    public SchemaReference getSchemaReference() {
        return schemaReference;
    }

    public Set<String> getAllowedWriteMethods() {
        return allowedWriteMethods;
    }

    public Set<String> getSubEntities() {
        return subEntities;
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
                ", readFilterConfig=" + readFilterConfig +
                ", writeFilterConfig=" + writeFilterConfig +
                ", schemaReference=" + schemaReference +
                ", allowedWriteMethods=" + allowedWriteMethods +
                ", subEntities=" + subEntities +
                '}';
    }

    public enum EndpointType {
        REST,
        GRAPHQL;

        /**
         * Parses string to EndpointType
         * This switch is acceptable - it's enum parsing from external string
         */
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

        /**
         * Gets the handler for this endpoint type
         * Polymorphic dispatch - no switch needed in ApiController!
         * 
         * @param restHandler The REST handler
         * @param graphQLHandler The GraphQL handler
         * @return The appropriate handler
         */
        public iaf.ofek.sigma.controller.EndpointHandler getHandler(
                iaf.ofek.sigma.controller.RestEndpointHandler restHandler,
                iaf.ofek.sigma.controller.GraphQLEndpointHandler graphQLHandler) {
            return switch (this) {
                case REST -> restHandler;
                case GRAPHQL -> graphQLHandler;
            };
        }
    }
}
