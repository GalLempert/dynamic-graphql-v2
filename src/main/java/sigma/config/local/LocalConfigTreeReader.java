package sigma.config.local;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sigma.zookeeper.ZookeeperTreeReader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * ZookeeperTreeReader replacement that serves configuration from a JSON file
 * instead of a live ZooKeeper connection. Used by local dev mode.
 *
 * The file uses the same nested format as zookeeper-import.json:
 *
 * <pre>
 * {
 *   "tree": {
 *     "/dev": {
 *       "data": "",
 *       "children": {
 *         "/dev/sigma/apiPrefix": { "data": "/api" },
 *         ...
 *       }
 *     }
 *   }
 * }
 * </pre>
 *
 * Only leaf nodes (nodes without children) carry values, matching the
 * semantics of the real tree reader.
 */
public class LocalConfigTreeReader extends ZookeeperTreeReader {

    private static final Logger logger = LoggerFactory.getLogger(LocalConfigTreeReader.class);

    private final Map<String, byte[]> leafNodes;

    public LocalConfigTreeReader(Map<String, byte[]> leafNodes) {
        super(null);
        this.leafNodes = Map.copyOf(leafNodes);
    }

    /**
     * Parses a zookeeper-import.json style file into a tree reader.
     */
    public static LocalConfigTreeReader fromJson(InputStream input) throws IOException {
        JsonNode root = new ObjectMapper().readTree(input);
        JsonNode tree = root.get("tree");
        if (tree == null || !tree.isObject()) {
            throw new IOException("Local config file must contain a top-level \"tree\" object");
        }

        Map<String, byte[]> leaves = new HashMap<>();
        collectLeaves(tree, leaves);
        logger.info("Loaded {} configuration nodes from local config file", leaves.size());
        return new LocalConfigTreeReader(leaves);
    }

    private static void collectLeaves(JsonNode nodes, Map<String, byte[]> leaves) {
        for (Map.Entry<String, JsonNode> entry : nodes.properties()) {
            String path = entry.getKey();
            JsonNode node = entry.getValue();

            JsonNode children = node.get("children");
            if (children != null && children.isObject() && !children.isEmpty()) {
                collectLeaves(children, leaves);
            } else {
                String data = node.hasNonNull("data") ? node.get("data").asText() : "";
                leaves.put(path, data.getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    @Override
    public Map<String, byte[]> readTree(String rootPath) {
        Map<String, byte[]> tree = new HashMap<>();
        String prefix = rootPath.endsWith("/") ? rootPath : rootPath + "/";
        leafNodes.forEach((path, data) -> {
            if (path.equals(rootPath) || path.startsWith(prefix)) {
                tree.put(path, data);
            }
        });
        return tree;
    }

    @Override
    public byte[] readNode(String path) {
        return leafNodes.get(path);
    }

    @Override
    public boolean nodeExists(String path) {
        if (leafNodes.containsKey(path)) {
            return true;
        }
        String prefix = path.endsWith("/") ? path : path + "/";
        return leafNodes.keySet().stream().anyMatch(p -> p.startsWith(prefix));
    }
}
