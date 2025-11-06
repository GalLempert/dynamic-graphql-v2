package sigma.zookeeper.event;

/**
 * Published when a ZooKeeper node is created or its data changes.
 */
public record ZookeeperNodeUpdatedEvent(String path, byte[] data) {
}
