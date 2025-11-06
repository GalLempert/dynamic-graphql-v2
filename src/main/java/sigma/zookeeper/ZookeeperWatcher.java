package sigma.zookeeper;

import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import sigma.zookeeper.event.ZookeeperNodeRemovedEvent;
import sigma.zookeeper.event.ZookeeperNodeUpdatedEvent;

import java.util.List;

@Component
public class ZookeeperWatcher implements Watcher {

    private static final Logger logger = LoggerFactory.getLogger(ZookeeperWatcher.class);
    private final ZooKeeper zooKeeper;
    private final ApplicationEventPublisher eventPublisher;

    public ZookeeperWatcher(ZooKeeper zooKeeper, ApplicationEventPublisher eventPublisher) {
        this.zooKeeper = zooKeeper;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void process(WatchedEvent event) {
        logger.info("Zookeeper event received: Type={}, State={}, Path={}",
                event.getType(), event.getState(), event.getPath());

        try {
            switch (event.getType()) {
                case NodeCreated -> {
                    logger.info("Node created: {}", event.getPath());
                    handleNodeChange(event.getPath());
                }
                case NodeDeleted -> {
                    logger.info("Node deleted: {}", event.getPath());
                    eventPublisher.publishEvent(new ZookeeperNodeRemovedEvent(event.getPath()));
                }
                case NodeDataChanged -> {
                    logger.info("Node data changed: {}", event.getPath());
                    handleNodeChange(event.getPath());
                }
                case NodeChildrenChanged -> {
                    logger.info("Node children changed: {}", event.getPath());
                    handleChildrenChange(event.getPath());
                }
                default -> logger.debug("Unhandled event type: {}", event.getType());
            }
        } catch (Exception e) {
            logger.error("Error processing Zookeeper event", e);
        }
    }

    /**
     * Watches a specific node for changes
     */
    public void watchNode(String path) throws KeeperException, InterruptedException {
        zooKeeper.exists(path, this);
        zooKeeper.getData(path, this, null);
    }

    /**
     * Watches all nodes in a tree recursively
     */
    public void watchTree(String rootPath) throws KeeperException, InterruptedException {
        try {
            // Watch the current node
            zooKeeper.getData(rootPath, this, null);

            // Get and watch children
            List<String> children = zooKeeper.getChildren(rootPath, this);

            // Recursively watch all children
            for (String child : children) {
                String childPath = rootPath.equals("/") ? "/" + child : rootPath + "/" + child;
                watchTree(childPath);
            }
        } catch (KeeperException.NoNodeException e) {
            logger.warn("Node does not exist: {}", rootPath);
        }
    }

    private void handleNodeChange(String path) throws KeeperException, InterruptedException {
        byte[] data = zooKeeper.getData(path, this, null);
        eventPublisher.publishEvent(new ZookeeperNodeUpdatedEvent(path, data));
    }

    private void handleChildrenChange(String path) throws KeeperException, InterruptedException {
        List<String> children = zooKeeper.getChildren(path, this);
        logger.debug("Children of {} changed. New count: {}", path, children.size());

        // Watch new children
        for (String child : children) {
            String childPath = path.equals("/") ? "/" + child : path + "/" + child;
            watchTree(childPath);
        }
    }
}
