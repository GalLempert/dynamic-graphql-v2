package sigma.persistence.dialect;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Factory for creating database dialect instances based on configuration.
 * Uses a supplier map instead of switch statements for extensibility.
 */
@Component
public class DatabaseDialectFactory {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseDialectFactory.class);

    private static final Map<DatabaseType, Supplier<DatabaseDialect>> DIALECT_SUPPLIERS = Map.of(
            DatabaseType.POSTGRESQL, PostgreSqlDialect::new,
            DatabaseType.ORACLE, OracleDialect::new,
            DatabaseType.H2, H2Dialect::new
    );

    /**
     * Creates a dialect instance for the specified database type.
     */
    public DatabaseDialect createDialect(DatabaseType type) {
        logger.info("Creating database dialect for type: {}", type);
        Supplier<DatabaseDialect> supplier = DIALECT_SUPPLIERS.get(type);
        if (supplier == null) {
            throw new IllegalArgumentException("No dialect available for type: " + type);
        }
        return supplier.get();
    }

    /**
     * Creates a dialect instance from a string identifier.
     */
    public DatabaseDialect createDialect(String typeIdentifier) {
        return createDialect(DatabaseType.fromString(typeIdentifier));
    }

    /**
     * Detects the database type from JDBC URL.
     */
    public DatabaseType detectFromUrl(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            logger.warn("No JDBC URL provided, defaulting to PostgreSQL");
            return DatabaseType.POSTGRESQL;
        }
        return DatabaseType.fromJdbcUrl(jdbcUrl).orElseGet(() -> {
            logger.warn("Could not detect database type from URL: {}, defaulting to PostgreSQL", jdbcUrl);
            return DatabaseType.POSTGRESQL;
        });
    }
}
