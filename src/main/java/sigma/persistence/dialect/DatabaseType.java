package sigma.persistence.dialect;

import java.util.List;
import java.util.Optional;

/**
 * Supported database types for the dynamic document service.
 * Each type knows its own JDBC URL markers for auto-detection.
 */
public enum DatabaseType {
    POSTGRESQL("postgresql", List.of(":postgresql:", ":postgres:")),
    ORACLE("oracle", List.of(":oracle:")),
    H2("h2", List.of(":h2:"));

    private final String identifier;
    private final List<String> jdbcUrlMarkers;

    DatabaseType(String identifier, List<String> jdbcUrlMarkers) {
        this.identifier = identifier;
        this.jdbcUrlMarkers = jdbcUrlMarkers;
    }

    public String getIdentifier() {
        return identifier;
    }

    /**
     * Parses a database type from string identifier (case-insensitive)
     */
    public static DatabaseType fromString(String value) {
        if (value == null || value.isBlank()) {
            return POSTGRESQL;
        }
        String normalized = value.toLowerCase().trim();
        for (DatabaseType type : values()) {
            if (type.identifier.equals(normalized) || type.name().equalsIgnoreCase(normalized)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown database type: " + value +
            ". Supported types: postgresql, oracle, h2");
    }

    /**
     * Detects the database type from a JDBC URL by matching known markers.
     */
    public static Optional<DatabaseType> fromJdbcUrl(String jdbcUrl) {
        String lowerUrl = jdbcUrl.toLowerCase();
        for (DatabaseType type : values()) {
            for (String marker : type.jdbcUrlMarkers) {
                if (lowerUrl.contains(marker)) {
                    return Optional.of(type);
                }
            }
        }
        return Optional.empty();
    }
}
