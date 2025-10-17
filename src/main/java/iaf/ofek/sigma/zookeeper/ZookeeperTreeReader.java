package iaf.ofek.sigma.zookeeper;

import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooKeeper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ZookeeperTreeReader {

    private static final Logger logger = LoggerFactory.getLogger(ZookeeperTreeReader.class);
    private final ZooKeeper zooKeeper;

    public ZookeeperTreeReader(ZooKeeper zooKeeper) {
        this.zooKeeper = zooKeeper;
    }

    /**
     * Reads the entire Zookeeper tree starting from the root path
     */
    public Map<String, byte[]> readTree(String rootPath) throws KeeperException, InterruptedException {
        Map<String, byte[]> tree = new HashMap<>();
        traverseTree(rootPath, tree);
        return tree;
    }

    /**
     * Recursively traverses the Zookeeper tree and collects all leaf nodes
     */
    private void traverseTree(String path, Map<String, byte[]> tree) throws KeeperException, InterruptedException {
        try {
            // Get the data at this node
            byte[] data = zooKeeper.getData(path, false, null);

            // Get children of this node
            List<String> children = zooKeeper.getChildren(path, false);

            if (children.isEmpty()) {
                // This is a leaf node, store it
                tree.put(path, data);
                logger.debug("Found leaf node: {} with data length: {}", path, data != null ? data.length : 0);
            } else {
                // This is an intermediate node, traverse children
                for (String child : children) {
                    String childPath = path.equals("/") ? "/" + child : path + "/" + child;
                    traverseTree(childPath, tree);
                }
            }
        } catch (KeeperException.NoNodeException e) {
            logger.warn("Node does not exist: {}", path);
        }
    }

    /**
     * Reads data from a specific Zookeeper node
     */
    public byte[] readNode(String path) throws KeeperException, InterruptedException {
        return zooKeeper.getData(path, false, null);
    }

    /**
     * Checks if a node exists
     */
    public boolean nodeExists(String path) throws KeeperException, InterruptedException {
        return zooKeeper.exists(path, false) != null;
    }
}
