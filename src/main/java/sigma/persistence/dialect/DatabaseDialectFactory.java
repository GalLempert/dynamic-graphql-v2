package sigma.persistence.dialect;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Factory for creating database dialect instances based on configuration.
 */
@Component
public class DatabaseDialectFactory {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseDialectFactory.class);

    /**
     * Creates a dialect instance for the specified database type.
     */
    public DatabaseDialect createDialect(DatabaseType type) {
        logger.info("Creating database dialect for type: {}", type);

        return switch (type) {
            case POSTGRESQL -> new PostgreSqlDialect();
            case ORACLE -> new OracleDialect();
            case H2 -> new H2Dialect();
        };
    }

    /**
     * Creates a dialect instance from a string identifier.
     */
    public DatabaseDialect createDialect(String typeIdentifier) {
        DatabaseType type = DatabaseType.fromString(typeIdentifier);
        return createDialect(type);
    }

    /**
     * Detects the database type from JDBC URL.
     */
    public DatabaseType detectFromUrl(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            logger.warn("No JDBC URL provided, defaulting to PostgreSQL");
            return DatabaseType.POSTGRESQL;
        }

        String lowerUrl = jdbcUrl.toLowerCase();
        if (lowerUrl.contains(":postgresql:") || lowerUrl.contains(":postgres:")) {
            return DatabaseType.POSTGRESQL;
        } else if (lowerUrl.contains(":oracle:")) {
            return DatabaseType.ORACLE;
        } else if (lowerUrl.contains(":h2:")) {
            return DatabaseType.H2;
        }

        logger.warn("Could not detect database type from URL: {}, defaulting to PostgreSQL", jdbcUrl);
        return DatabaseType.POSTGRESQL;
    }
}
