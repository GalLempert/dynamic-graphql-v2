package sigma.filter;

import sigma.model.filter.FilterOperator;
import sigma.model.filter.node.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Parses filter maps into FilterNode trees
 * Converts raw Map structures into strongly-typed filter nodes
 */
@Component
public class FilterParser {

    /**
     * Parses a filter map into a FilterNode tree
     *
     * @param filterMap Raw filter map from JSON
     * @return Root FilterNode
     */
    public FilterNode parse(Map<String, Object> filterMap) {
        if (filterMap == null || filterMap.isEmpty()) {
            return new CompositeFilterNode(List.of());
        }

        List<FilterNode> nodes = new ArrayList<>();

        for (Map.Entry<String, Object> entry : filterMap.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (key.startsWith("$")) {
                // Logical operator
                FilterNode logicalNode = parseLogicalOperator(key, value);
                nodes.add(logicalNode);
            } else {
                // Field filter
                FilterNode fieldNode = parseFieldFilter(key, value);
                nodes.add(fieldNode);
            }
        }

        // If only one node, return it directly
        if (nodes.size() == 1) {
            return nodes.get(0);
        }

        // Multiple nodes are implicitly ANDed
        return new CompositeFilterNode(nodes);
    }

    /**
     * Parses a logical operator node
     */
    private FilterNode parseLogicalOperator(String operatorSymbol, Object value) {
        FilterOperator operator = FilterOperator.fromString(operatorSymbol);

        if (!operator.isLogical()) {
            throw new IllegalArgumentException("Expected logical operator, got: " + operatorSymbol);
        }

        List<FilterNode> children = new ArrayList<>();

        if (operator == FilterOperator.NOT) {
            // $not takes a single condition (object)
            if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> notCondition = (Map<String, Object>) value;
                children.add(parse(notCondition));
            } else {
                throw new IllegalArgumentException("$not operator requires an object value");
            }
        } else {
            // $and, $or, $nor take arrays of conditions
            if (!(value instanceof List)) {
                throw new IllegalArgumentException(operatorSymbol + " operator requires an array value");
            }

            List<?> conditions = (List<?>) value;
            for (Object condition : conditions) {
                if (condition instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> conditionMap = (Map<String, Object>) condition;
                    children.add(parse(conditionMap));
                } else {
                    throw new IllegalArgumentException("Invalid condition type in " + operatorSymbol + ": expected object");
                }
            }
        }

        return new LogicalFilterNode(operator, children);
    }

    /**
     * Parses a field filter node
     */
    private FilterNode parseFieldFilter(String fieldName, Object value) {
        if (value instanceof Map) {
            // Field has operators: { "price": { "$gte": 100, "$lte": 500 } }
            @SuppressWarnings("unchecked")
            Map<String, Object> operatorMap = (Map<String, Object>) value;
            return new FieldFilterNode(fieldName, operatorMap);
        } else {
            // Direct equality: { "category": "electronics" }
            return new FieldFilterNode(fieldName, value);
        }
    }
}
