package sigma.config;

import org.apache.zookeeper.ZooKeeper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

/**
 * Creates the real ZooKeeper client connection.
 * Disabled by setting zookeeper.enabled=false (e.g. in tests, which provide a mock client).
 */
@Configuration
@ConditionalOnProperty(name = "zookeeper.enabled", havingValue = "true", matchIfMissing = true)
public class ZookeeperConfig {

    @Value("${zookeeper.connection-string}")
    private String connectionString;

    @Value("${zookeeper.session-timeout}")
    private int sessionTimeout;

    @Bean
    public ZooKeeper zooKeeper() throws IOException, InterruptedException {
        CountDownLatch connectionLatch = new CountDownLatch(1);

        ZooKeeper zooKeeper = new ZooKeeper(connectionString, sessionTimeout, event -> {
            if (event.getState() == org.apache.zookeeper.Watcher.Event.KeeperState.SyncConnected) {
                connectionLatch.countDown();
            }
        });

        connectionLatch.await();
        return zooKeeper;
    }
}
