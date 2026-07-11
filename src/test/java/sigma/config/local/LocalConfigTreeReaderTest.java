package sigma.config.local;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LocalConfigTreeReaderTest {

    private static final String CONFIG = """
        {
          "tree": {
            "/dev": {
              "data": "",
              "children": {
                "/dev/sigma": {
                  "data": "",
                  "children": {
                    "/dev/sigma/apiPrefix": { "data": "/api" },
                    "/dev/sigma/endpoints": {
                      "data": "",
                      "children": {
                        "/dev/sigma/endpoints/products/path": { "data": "/products" },
                        "/dev/sigma/endpoints/products/httpMethod": { "data": "GET,POST" }
                      }
                    }
                  }
                },
                "/dev/Globals": {
                  "data": "",
                  "children": {
                    "/dev/Globals/EnableEnumService": { "data": "false" }
                  }
                }
              }
            }
          }
        }
        """;

    private LocalConfigTreeReader reader;

    @BeforeEach
    void setUp() throws IOException {
        reader = LocalConfigTreeReader.fromJson(
                new ByteArrayInputStream(CONFIG.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("Only leaf nodes are exposed, keyed by full path")
    void testLeafNodes() {
        assertEquals("/api", asString(reader.readNode("/dev/sigma/apiPrefix")));
        assertEquals("/products", asString(reader.readNode("/dev/sigma/endpoints/products/path")));
        // Intermediate nodes are not leaves
        assertNull(reader.readNode("/dev/sigma"));
        assertNull(reader.readNode("/dev/sigma/endpoints"));
    }

    @Test
    @DisplayName("readTree returns all leaves under a prefix")
    void testReadTree() {
        Map<String, byte[]> serviceTree = reader.readTree("/dev/sigma");
        assertEquals(3, serviceTree.size());
        assertTrue(serviceTree.containsKey("/dev/sigma/apiPrefix"));
        assertTrue(serviceTree.containsKey("/dev/sigma/endpoints/products/path"));
        assertTrue(serviceTree.containsKey("/dev/sigma/endpoints/products/httpMethod"));

        Map<String, byte[]> globalsTree = reader.readTree("/dev/Globals");
        assertEquals(1, globalsTree.size());
    }

    @Test
    @DisplayName("readTree does not mix sibling trees with a shared name prefix")
    void testReadTreePrefixBoundary() {
        assertTrue(reader.readTree("/dev/sig").isEmpty(),
                "/dev/sig must not match /dev/sigma nodes");
    }

    @Test
    @DisplayName("nodeExists is true for leaves and intermediate paths")
    void testNodeExists() {
        assertTrue(reader.nodeExists("/dev/sigma/apiPrefix"));
        assertTrue(reader.nodeExists("/dev/sigma"));
        assertTrue(reader.nodeExists("/dev/sigma/endpoints"));
        assertFalse(reader.nodeExists("/dev/other"));
        assertFalse(reader.nodeExists("/prod"));
    }

    @Test
    @DisplayName("File without a tree object is rejected")
    void testMissingTree() {
        assertThrows(IOException.class, () -> LocalConfigTreeReader.fromJson(
                new ByteArrayInputStream("{\"foo\": 1}".getBytes(StandardCharsets.UTF_8))));
    }

    private String asString(byte[] data) {
        return data == null ? null : new String(data, StandardCharsets.UTF_8);
    }
}
