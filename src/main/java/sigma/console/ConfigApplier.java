package sigma.console;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import sigma.controller.EndpointRegistry;
import sigma.zookeeper.ZookeeperConfigService;
import sigma.zookeeper.event.ZookeeperNodeRemovedEvent;
import sigma.zookeeper.event.ZookeeperNodeUpdatedEvent;

import java.util.List;
import java.util.Map;

/**
 * Applies compiled configuration nodes to the running system.
 *
 * Two layers, in order:
 * 1. Durable store: when a real ZooKeeper is connected, nodes are written
 *    there (parents created as needed) so the change survives restarts and
 *    reaches every instance through the existing watchers.
 * 2. This instance: node-updated events refresh the in-memory configuration
 *    (identical to what a watcher delivers), then the endpoint registry is
 *    reloaded so new routes are live immediately.
 *
 * In local dev mode (no ZooKeeper) only layer 2 applies - changes are live
 * instantly and the Git repository is the durable record.
 */
@Component
@ConditionalOnProperty(name = "sigma.console.edit.enabled", havingValue = "true")
public class ConfigApplier {

    private static final Logger logger = LoggerFactory.getLogger(ConfigApplier.class);

    private final ApplicationEventPublisher eventPublisher;
    private final EndpointRegistry endpointRegistry;
    private final ZookeeperConfigService configService;
    private final ObjectProvider<ZooKeeper> zooKeeper;

    public ConfigApplier(ApplicationEventPublisher eventPublisher,
                         EndpointRegistry endpointRegistry,
                         ZookeeperConfigService configService,
                         ObjectProvider<ZooKeeper> zooKeeper) {
        this.eventPublisher = eventPublisher;
        this.endpointRegistry = endpointRegistry;
        this.configService = configService;
        this.zooKeeper = zooKeeper;
    }

    /**
     * Replaces the configuration subtree at {@code subtreeBasePath} with
     * {@code nodes}: new/changed nodes are written and nodes that existed
     * under the subtree but are absent from the new set are removed, so
     * revoking a capability (a search field, a write method) actually
     * takes effect.
     */
    public void apply(String subtreeBasePath, Map<String, byte[]> nodes) {
        String prefix = subtreeBasePath.endsWith("/") ? subtreeBasePath : subtreeBasePath + "/";
        List<String> stale = configService.getAllConfiguration().keySet().stream()
                .filter(path -> path.startsWith(prefix) && !nodes.containsKey(path))
                .toList();

        ZooKeeper zk = zooKeeper.getIfAvailable();
        if (zk != null) {
            nodes.forEach((path, data) -> writeZnode(zk, path, data));
            stale.forEach(path -> deleteZnode(zk, path));
        }

        stale.forEach(path -> eventPublisher.publishEvent(new ZookeeperNodeRemovedEvent(path)));
        nodes.forEach((path, data) -> eventPublisher.publishEvent(new ZookeeperNodeUpdatedEvent(path, data)));
        endpointRegistry.loadEndpoints();

        logger.info("Applied {} configuration nodes, removed {} stale nodes ({} mode)",
                nodes.size(), stale.size(), zk != null ? "ZooKeeper" : "local");
    }

    private void writeZnode(ZooKeeper zk, String path, byte[] data) {
        try {
            ensureParents(zk, path);
            if (zk.exists(path, false) == null) {
                zk.create(path, data, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            } else {
                zk.setData(path, data, -1);
            }
        } catch (KeeperException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("Failed to write ZooKeeper node " + path, e);
        }
    }

    private void deleteZnode(ZooKeeper zk, String path) {
        try {
            if (zk.exists(path, false) != null) {
                zk.delete(path, -1);
            }
        } catch (KeeperException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("Failed to delete ZooKeeper node " + path, e);
        }
    }

    private void ensureParents(ZooKeeper zk, String path) throws KeeperException, InterruptedException {
        String[] segments = path.split("/");
        StringBuilder current = new StringBuilder();
        // skip the final segment (the leaf itself) and the leading empty segment
        for (int i = 1; i < segments.length - 1; i++) {
            current.append('/').append(segments[i]);
            String parent = current.toString();
            if (zk.exists(parent, false) == null) {
                try {
                    zk.create(parent, new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
                } catch (KeeperException.NodeExistsException ignored) {
                    // created concurrently - fine
                }
            }
        }
    }
}
