package sigma.config;

import sigma.config.properties.ZookeeperConfigProperties;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.AbstractMongoClientConfiguration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableMongoRepositories(basePackages = "sigma.persistence.repository")
@EnableMongoAuditing  // Enable Spring Data auditing for @CreatedDate, @LastModifiedDate, etc.
@EnableTransactionManagement  // Enable @Transactional support for ACID operations
public class MongoConfig extends AbstractMongoClientConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(MongoConfig.class);

    private final ZookeeperConfigProperties configProperties;

    public MongoConfig(ZookeeperConfigProperties configProperties) {
        this.configProperties = configProperties;
    }

    @Override
    protected String getDatabaseName() {
        String database = configProperties.getMongoDatabase();
        logger.info("MongoDB database: {}", database);
        return database;
    }

    @Override
    @Bean
    public MongoClient mongoClient() {
        String host = configProperties.getMongoHost();
        String port = configProperties.getMongoPort();
        String username = configProperties.getMongoUsername();
        String password = configProperties.getMongoPassword();
        String authDatabase = configProperties.getMongoAuthDatabase();

        String connectionString;
        if (username != null && !username.isEmpty() && password != null && !password.isEmpty()) {
            connectionString = String.format("mongodb://%s:%s@%s:%s/%s?authSource=%s",
                    username, password, host, port, getDatabaseName(), authDatabase);
            logger.info("MongoDB connection with authentication: mongodb://***:***@{}:{}/{}?authSource={}", host, port, getDatabaseName(), authDatabase);
        } else {
            connectionString = String.format("mongodb://%s:%s/%s", host, port, getDatabaseName());
            logger.info("MongoDB connection without authentication: mongodb://{}:{}/{}", host, port, getDatabaseName());
        }

        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(connectionString))
                .build();

        logger.info("MongoDB client created with ACID transaction support enabled");
        return MongoClients.create(settings);
    }

    /**
     * Enable transaction manager for @Transactional support
     * Note: Transactions require MongoDB 4.0+ with replica set
     */
    @Bean
    public org.springframework.data.mongodb.MongoTransactionManager transactionManager(
            org.springframework.data.mongodb.MongoDatabaseFactory dbFactory) {
        logger.info("MongoDB transaction manager configured for ACID operations");
        return new org.springframework.data.mongodb.MongoTransactionManager(dbFactory);
    }
}
