package sigma.config;

import sigma.config.properties.ZookeeperConfigProperties;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.Properties;

@Configuration
@EnableJpaRepositories(basePackages = "sigma.persistence.repository")
@EnableJpaAuditing  // Enable Spring Data auditing for @CreatedDate, @LastModifiedDate, etc.
@EnableTransactionManagement  // Enable @Transactional support for ACID operations
public class PostgresConfig {

    private static final Logger logger = LoggerFactory.getLogger(PostgresConfig.class);

    private final ZookeeperConfigProperties configProperties;

    public PostgresConfig(ZookeeperConfigProperties configProperties) {
        this.configProperties = configProperties;
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
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(dataSource);
        em.setPackagesToScan("sigma.model", "sigma.persistence.entity");

        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        vendorAdapter.setGenerateDdl(true);
        vendorAdapter.setShowSql(false);
        em.setJpaVendorAdapter(vendorAdapter);

        Properties properties = new Properties();
        properties.setProperty("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        properties.setProperty("hibernate.hbm2ddl.auto", "update");
        properties.setProperty("hibernate.show_sql", "false");
        properties.setProperty("hibernate.format_sql", "true");
        // Enable JSONB type handling
        properties.setProperty("hibernate.types.jackson.object.mapper", "sigma.config.JsonbObjectMapperSupplier");

        em.setJpaProperties(properties);

        logger.info("PostgreSQL entity manager configured with JSONB support");
        return em;
    }

    @Bean
    public PlatformTransactionManager transactionManager(LocalContainerEntityManagerFactoryBean entityManagerFactory) {
        JpaTransactionManager transactionManager = new JpaTransactionManager();
        transactionManager.setEntityManagerFactory(entityManagerFactory.getObject());
        logger.info("PostgreSQL transaction manager configured for ACID operations");
        return transactionManager;
    }
}
