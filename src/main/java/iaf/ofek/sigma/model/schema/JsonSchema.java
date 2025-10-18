package iaf.ofek.sigma.model.schema;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Represents a JSON Schema for document validation
 *
 * JSON Schemas are stored in ZooKeeper and cached in memory
 * They define:
 * - Required fields
 * - Field types
 * - Field constraints (min, max, pattern, etc.)
 * - Nested object structures
 *
 * Special schema: "base-types"
 * - Defines common type definitions
 * - Referenced by other schemas via $ref
 */
public class JsonSchema {

    private final String name;
    private final JsonNode schema;

    public JsonSchema(String name, JsonNode schema) {
        this.name = name;
        this.schema = schema;
    }

    public String getName() {
        return name;
    }

    public JsonNode getSchema() {
        return schema;
    }

    /**
     * Returns true if this is the base types schema
     */
    public boolean isBaseSchema() {
        return "base-types".equals(name);
    }

    @Override
    public String toString() {
        return "JsonSchema{" +
                "name='" + name + '\'' +
                ", schema=" + schema +
                '}';
    }
}
