package sigma.model.filter.node;

import sigma.model.filter.FilterConfig;
import sigma.model.filter.FilterOperator;
import sigma.model.filter.SqlPredicate;
import sigma.model.filter.operator.OperatorStrategy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Represents a field filter node in the filter tree
 * Example: { "price": { "$gte": 100 } }
 */
public class FieldFilterNode extends FilterNode {

    private final String fieldName;
    private final Map<String, Object> operators; // operator -> value

    public FieldFilterNode(String fieldName, Map<String, Object> operators) {
        this.fieldName = fieldName;
        this.operators = operators;
    }

    /**
     * Constructor for simple equality: { "fieldName": value }
     */
    public FieldFilterNode(String fieldName, Object value) {
        this.fieldName = fieldName;
        this.operators = Map.of("$eq", value);
    }

    @Override
    public SqlPredicate toPredicate() {
        if (operators.isEmpty()) {
            return new SqlPredicate("1=1");
        }

        // If only one operator, apply directly
        if (operators.size() == 1) {
            Map.Entry<String, Object> entry = operators.entrySet().iterator().next();
            String operatorSymbol = entry.getKey();
            Object value = entry.getValue();

            FilterOperator operator = FilterOperator.fromString(operatorSymbol);
            return operator.getStrategy().apply(fieldName, value);
        }

        // Multiple operators on same field - AND them together
        List<SqlPredicate> predicates = new ArrayList<>();
        for (Map.Entry<String, Object> entry : operators.entrySet()) {
            String operatorSymbol = entry.getKey();
            Object value = entry.getValue();

            FilterOperator operator = FilterOperator.fromString(operatorSymbol);
            OperatorStrategy strategy = operator.getStrategy();

            predicates.add(strategy.apply(fieldName, value));
        }

        return SqlPredicate.and(predicates.toArray(new SqlPredicate[0]));
    }

    @Override
    public List<String> validate(FilterConfig config) {
        List<String> errors = new ArrayList<>();

        // Check if field is filterable
        if (!config.isFieldFilterable(fieldName)) {
            errors.add("Field '" + fieldName + "' is not filterable");
            return errors;
        }

        // Validate each operator
        for (Map.Entry<String, Object> entry : operators.entrySet()) {
            String operatorSymbol = entry.getKey();
            Object value = entry.getValue();

            if (!operatorSymbol.startsWith("$")) {
                errors.add("Expected operator (starting with $) but got '" + operatorSymbol + "' for field '" + fieldName + "'");
                continue;
            }

            try {
                FilterOperator operator = FilterOperator.fromString(operatorSymbol);

                // Check if operator is allowed for this field
                if (!config.isOperatorAllowed(fieldName, operator)) {
                    errors.add("Operator " + operatorSymbol + " is not allowed for field '" + fieldName + "'. Allowed: " +
                              config.getAllowedOperators(fieldName));
                    continue;
                }

                // Validate value type
                if (!operator.getStrategy().isValidValue(value)) {
                    errors.add("Invalid value type for operator " + operatorSymbol + " on field '" + fieldName + "'");
                }

            } catch (IllegalArgumentException e) {
                errors.add("Unknown operator for field '" + fieldName + "': " + operatorSymbol);
            }
        }

        return errors;
    }

    public String getFieldName() {
        return fieldName;
    }

    public Map<String, Object> getOperators() {
        return operators;
    }

    @Override
    public String toString() {
        return "FieldFilter{" + fieldName + ": " + operators + "}";
    }
}
