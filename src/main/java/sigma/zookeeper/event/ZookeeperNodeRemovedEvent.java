package sigma.zookeeper.event;

/**
 * Published when a ZooKeeper node is deleted.
 */
public record ZookeeperNodeRemovedEvent(String path) {
}
