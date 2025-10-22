package sigma.service.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import sigma.model.enums.DynamicEnum;
import sigma.model.enums.EnumFieldBinding;
import sigma.model.enums.EnumFieldPointer;
import sigma.service.enums.EnumRegistry;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Augments JSON schemas by replacing enum placeholders with actual enum values
 * and recording field bindings for response transformation.
 */
public class EnumSchemaAugmentor {

    private static final String ENUM_REF_FIELD = "enumRef";

    private final EnumRegistry enumRegistry;

    public EnumSchemaAugmentor(EnumRegistry enumRegistry) {
        this.enumRegistry = enumRegistry;
    }

    public Result augment(ObjectNode schemaNode) {
        List<EnumFieldBinding> bindings = new ArrayList<>();
        traverse(schemaNode, new ArrayDeque<>(), bindings);
        return new Result(schemaNode, bindings);
    }

    private void traverse(JsonNode node, Deque<EnumFieldPointer.Segment> path, List<EnumFieldBinding> bindings) {
        if (node == null || !node.isObject()) {
            return;
        }

        ObjectNode objectNode = (ObjectNode) node;
        if (objectNode.has(ENUM_REF_FIELD)) {
            String enumName = objectNode.get(ENUM_REF_FIELD).asText();
            DynamicEnum dynamicEnum = enumRegistry.getEnum(enumName)
                    .orElseThrow(() -> new IllegalStateException("Enum '" + enumName + "' not found for schema placeholder"));

            ArrayNode enumValues = objectNode.putArray("enum");
            dynamicEnum.getValuesByCode().values().forEach(value -> enumValues.add(value.getCode()));
            objectNode.remove(ENUM_REF_FIELD);

            bindings.add(new EnumFieldBinding(new EnumFieldPointer(List.copyOf(path)), enumName));
        }

        if (objectNode.has("properties")) {
            JsonNode propertiesNode = objectNode.get("properties");
            if (propertiesNode.isObject()) {
                propertiesNode.fields().forEachRemaining(entry -> {
                    path.addLast(new EnumFieldPointer.PropertySegment(entry.getKey()));
                    traverse(entry.getValue(), path, bindings);
                    path.removeLast();
                });
            }
        }

        if (objectNode.has("items")) {
            JsonNode itemsNode = objectNode.get("items");
            path.addLast(EnumFieldPointer.ArraySegment.INSTANCE);
            traverse(itemsNode, path, bindings);
            path.removeLast();
        }

        if (objectNode.has("allOf")) {
            JsonNode allOfNode = objectNode.get("allOf");
            if (allOfNode.isArray()) {
                for (JsonNode item : allOfNode) {
                    traverse(item, path, bindings);
                }
            }
        }

        if (objectNode.has("anyOf")) {
            JsonNode anyOfNode = objectNode.get("anyOf");
            if (anyOfNode.isArray()) {
                for (JsonNode item : anyOfNode) {
                    traverse(item, path, bindings);
                }
            }
        }
    }

    public record Result(ObjectNode schema, List<EnumFieldBinding> bindings) {
    }
}
