package sigma.persistence.dialect;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Initializes the database schema based on the configured dialect.
 * Runs after application startup to create tables and indexes.
 */
@Component
public class DatabaseInitializer {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseInitializer.class);

    private final JdbcTemplate jdbcTemplate;
    private final DatabaseDialect dialect;

    public DatabaseInitializer(JdbcTemplate jdbcTemplate, DatabaseDialect dialect) {
        this.jdbcTemplate = jdbcTemplate;
        this.dialect = dialect;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initializeSchema() {
        logger.info("Initializing database schema for dialect: {}", dialect.getType());

        try {
            // Create table
            String createTableSql = dialect.getCreateTableSql();
            executeIfNotExists(createTableSql, "dynamic_documents");
            logger.info("Table dynamic_documents ready");

            // Create indexes
            List<String> indexSql = dialect.getCreateIndexesSql();
            for (String sql : indexSql) {
                executeSafely(sql, "index");
            }
            logger.info("Indexes created");

            // Create sequences and triggers
            List<String> sequenceSql = dialect.getSequenceSupportSql();
            for (String sql : sequenceSql) {
                executeSafely(sql, "sequence/trigger");
            }
            logger.info("Sequence support configured");

            logger.info("Database schema initialization completed for {}", dialect.getType());

        } catch (Exception e) {
            logger.error("Failed to initialize database schema for {}: {}",
                dialect.getType(), e.getMessage(), e);
            // Don't fail startup - schema might already exist
        }
    }

    private void executeIfNotExists(String sql, String objectName) {
        try {
            // For most databases, CREATE TABLE IF NOT EXISTS handles this
            jdbcTemplate.execute(sql);
        } catch (Exception e) {
            // Table might already exist
            logger.debug("{} might already exist: {}", objectName, e.getMessage());
        }
    }

    private void executeSafely(String sql, String description) {
        try {
            jdbcTemplate.execute(sql);
        } catch (Exception e) {
            // Object might already exist or SQL might not be applicable
            logger.debug("Could not execute {} SQL: {}", description, e.getMessage());
        }
    }
}
