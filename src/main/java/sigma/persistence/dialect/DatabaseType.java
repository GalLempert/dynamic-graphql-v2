package sigma.persistence.dialect;

/**
 * Supported database types for the dynamic document service.
 */
public enum DatabaseType {
    POSTGRESQL("postgresql"),
    ORACLE("oracle"),
    H2("h2");

    private final String identifier;

    DatabaseType(String identifier) {
        this.identifier = identifier;
    }

    public String getIdentifier() {
        return identifier;
    }

    /**
     * Parses a database type from string identifier (case-insensitive)
     */
    public static DatabaseType fromString(String value) {
        if (value == null || value.isBlank()) {
            return POSTGRESQL; // Default
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
}
