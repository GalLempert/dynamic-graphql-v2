package sigma.config.local;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import sigma.zookeeper.ZookeeperTreeReader;
import sigma.zookeeper.ZookeeperWatcher;

import java.io.IOException;
import java.io.InputStream;

/**
 * Local development mode: serves endpoint configuration from a JSON file
 * instead of ZooKeeper, so the service can run fully self-contained
 * (together with the h2 database settings in the "local" profile).
 *
 * Activate with the "local" Spring profile, or manually with:
 *   zookeeper.enabled=false
 *   sigma.local-config.enabled=true
 *   sigma.local-config.file=classpath:local-config.json  (or file:/path/to/config.json)
 *
 * The file uses the same nested tree format as zookeeper-import.json,
 * so the same document can be imported into a real ZooKeeper later.
 */
@Configuration
@ConditionalOnProperty(name = "sigma.local-config.enabled", havingValue = "true")
public class LocalDevConfig {

    private static final Logger logger = LoggerFactory.getLogger(LocalDevConfig.class);

    @Bean
    public ZookeeperTreeReader zookeeperTreeReader(
            @Value("${sigma.local-config.file:classpath:local-config.json}") Resource configFile) throws IOException {
        logger.info("Local dev mode active - loading configuration from {}", configFile.getDescription());
        try (InputStream input = configFile.getInputStream()) {
            return LocalConfigTreeReader.fromJson(input);
        }
    }

    @Bean
    public ZookeeperWatcher zookeeperWatcher(ApplicationEventPublisher eventPublisher) {
        return new NoOpZookeeperWatcher(eventPublisher);
    }
}
