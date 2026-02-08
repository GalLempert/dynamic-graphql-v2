package sigma.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import org.postgresql.util.PGobject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.jdbc.core.convert.JdbcCustomConversions;
import org.springframework.data.jdbc.repository.config.AbstractJdbcConfiguration;
import org.springframework.data.jdbc.repository.config.EnableJdbcAuditing;
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import sigma.config.properties.ZookeeperConfigProperties;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Configuration
@EnableJdbcRepositories(basePackages = "sigma.persistence.repository")
@EnableJdbcAuditing
@EnableTransactionManagement
public class PostgresConfig extends AbstractJdbcConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(PostgresConfig.class);

    private final ZookeeperConfigProperties configProperties;
    private final ObjectMapper objectMapper;

    public PostgresConfig(ZookeeperConfigProperties configProperties, ObjectMapper objectMapper) {
        this.configProperties = configProperties;
        this.objectMapper = objectMapper;
    }

    @Bean
    public DataSource dataSource() {
        String host = configProperties.getPostgresHost();
        String port = configProperties.getPostgresPort();
        String database = configProperties.getPostgresDatabase();
        String username = configProperties.getPostgresUsername();
        String password = configProperties.getPostgresPassword();

        String jdbcUrl = String.format("jdbc:postgresql://%s:%s/%s", host, port, database);

        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(jdbcUrl);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        dataSource.setDriverClassName("org.postgresql.Driver");

        // Connection pool settings
        dataSource.setMaximumPoolSize(10);
        dataSource.setMinimumIdle(2);
        dataSource.setConnectionTimeout(30000);
        dataSource.setIdleTimeout(600000);
        dataSource.setMaxLifetime(1800000);

        if (username != null && !username.isEmpty()) {
            logger.info("PostgreSQL connection with authentication: jdbc:postgresql://{}:{}/{}", host, port, database);
        } else {
            logger.info("PostgreSQL connection without authentication: jdbc:postgresql://{}:{}/{}", host, port, database);
        }

        return dataSource;
    }

    @Bean
    public NamedParameterJdbcOperations namedParameterJdbcOperations(DataSource dataSource) {
        return new NamedParameterJdbcTemplate(dataSource);
    }

    @Bean
    public PlatformTransactionManager transactionManager(DataSource dataSource) {
        DataSourceTransactionManager transactionManager = new DataSourceTransactionManager(dataSource);
        logger.info("PostgreSQL transaction manager configured");
        return transactionManager;
    }

    @Override
    public JdbcCustomConversions jdbcCustomConversions() {
        return new JdbcCustomConversions(Arrays.asList(
                new MapToJsonbConverter(objectMapper),
                new JsonbToMapConverter(objectMapper)
        ));
    }

    /**
     * Converter: Map -> PGobject (JSONB)
     */
    static class MapToJsonbConverter implements Converter<Map<String, Object>, PGobject> {
        private final ObjectMapper objectMapper;

        MapToJsonbConverter(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        @Override
        public PGobject convert(Map<String, Object> source) {
            PGobject jsonObject = new PGobject();
            jsonObject.setType("jsonb");
            try {
                jsonObject.setValue(source == null ? "{}" : objectMapper.writeValueAsString(source));
            } catch (SQLException | IOException e) {
                throw new RuntimeException("Failed to convert Map to JSONB", e);
            }
            return jsonObject;
        }
    }

    /**
     * Converter: PGobject (JSONB) -> Map
     */
    static class JsonbToMapConverter implements Converter<PGobject, Map<String, Object>> {
        private final ObjectMapper objectMapper;

        JsonbToMapConverter(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Map<String, Object> convert(PGobject source) {
            if (source == null || source.getValue() == null) {
                return new HashMap<>();
            }
            try {
                return objectMapper.readValue(source.getValue(), Map.class);
            } catch (IOException e) {
                throw new RuntimeException("Failed to convert JSONB to Map", e);
            }
        }
    }
}
