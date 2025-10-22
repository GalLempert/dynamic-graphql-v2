package sigma.model.schema;

import com.fasterxml.jackson.databind.JsonNode;
import sigma.model.enums.EnumFieldBinding;

import java.util.List;

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
    private final List<EnumFieldBinding> enumBindings;

    public JsonSchema(String name, JsonNode schema, List<EnumFieldBinding> enumBindings) {
        this.name = name;
        this.schema = schema;
        this.enumBindings = enumBindings;
    }

    public String getName() {
        return name;
    }

    public JsonNode getSchema() {
        return schema;
    }

    public List<EnumFieldBinding> getEnumBindings() {
        return enumBindings;
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
                ", enumBindings=" + enumBindings +
                '}';
    }
}
