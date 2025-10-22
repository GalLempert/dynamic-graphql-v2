package sigma.config;

import org.apache.zookeeper.ZooKeeper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

@Configuration
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
