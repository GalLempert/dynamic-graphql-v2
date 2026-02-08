package sigma.persistence.dialect;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import sigma.model.filter.SqlPredicateFactory;

/**
 * Configuration for database dialect selection.
 * The database type can be configured via:
 * 1. ZooKeeper: /{ENV}/{SERVICE}/database/type
 * 2. Application property: sigma.database.type
 * 3. Auto-detection from JDBC URL
 */
@Configuration
public class DatabaseDialectConfig {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseDialectConfig.class);

    @Value("${sigma.database.type:#{null}}")
    private String configuredDatabaseType;

    @Value("${spring.datasource.url:}")
    private String jdbcUrl;

    private final DatabaseDialectFactory dialectFactory;

    public DatabaseDialectConfig(DatabaseDialectFactory dialectFactory) {
        this.dialectFactory = dialectFactory;
    }

    @Bean
    @Primary
    public DatabaseDialect databaseDialect() {
        DatabaseType type = determineDatabaseType();
        logger.info("Initializing database dialect: {}", type);
        DatabaseDialect dialect = dialectFactory.createDialect(type);

        // Configure the static SqlPredicateFactory with the dialect
        SqlPredicateFactory.setDialect(dialect);
        logger.info("SqlPredicateFactory configured with dialect: {}", type);

        return dialect;
    }

    private DatabaseType determineDatabaseType() {
        // Priority 1: Explicit configuration
        if (configuredDatabaseType != null && !configuredDatabaseType.isBlank()) {
            logger.info("Using configured database type: {}", configuredDatabaseType);
            return DatabaseType.fromString(configuredDatabaseType);
        }

        // Priority 2: Auto-detect from JDBC URL
        if (jdbcUrl != null && !jdbcUrl.isBlank()) {
            DatabaseType detected = dialectFactory.detectFromUrl(jdbcUrl);
            logger.info("Auto-detected database type from URL: {}", detected);
            return detected;
        }

        // Default: PostgreSQL
        logger.info("Using default database type: POSTGRESQL");
        return DatabaseType.POSTGRESQL;
    }
}
