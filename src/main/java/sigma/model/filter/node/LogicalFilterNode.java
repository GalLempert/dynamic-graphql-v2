package sigma.model.filter.node;

import sigma.model.filter.FilterConfig;
import sigma.model.filter.FilterOperator;
import sigma.model.filter.operator.LogicalOperator;
import org.springframework.data.mongodb.core.query.Criteria;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Represents a logical operator node in the filter tree
 * Example: { "$and": [...] }
 */
public class LogicalFilterNode extends FilterNode {

    private final FilterOperator operator;
    private final List<FilterNode> children;

    public LogicalFilterNode(FilterOperator operator, List<FilterNode> children) {
        if (!operator.isLogical()) {
            throw new IllegalArgumentException("Operator must be logical: " + operator);
        }
        this.operator = operator;
        this.children = children;
    }

    @Override
    public Criteria toCriteria() {
        LogicalOperator logicalOp = (LogicalOperator) operator.getStrategy();

        List<Criteria> childCriteria = children.stream()
                .map(FilterNode::toCriteria)
                .collect(Collectors.toList());

        return logicalOp.applyCriteria(childCriteria);
    }

    @Override
    public List<String> validate(FilterConfig config) {
        List<String> errors = new ArrayList<>();

        // Validate structure
        if (operator == FilterOperator.NOT && children.size() != 1) {
            errors.add("$not operator must have exactly one condition, got " + children.size());
        } else if (children.isEmpty()) {
            errors.add(operator.getMongoOperator() + " operator must have at least one condition");
        }

        // Validate all children
        for (int i = 0; i < children.size(); i++) {
            FilterNode child = children.get(i);
            List<String> childErrors = child.validate(config);

            // Add context to child errors
            final int childIndex = i;  // Make effectively final for lambda
            childErrors = childErrors.stream()
                    .map(err -> operator.getMongoOperator() + "[" + childIndex + "]: " + err)
                    .collect(Collectors.toList());

            errors.addAll(childErrors);
        }

        return errors;
    }

    public FilterOperator getOperator() {
        return operator;
    }

    public List<FilterNode> getChildren() {
        return children;
    }

    @Override
    public String toString() {
        return "LogicalFilter{" + operator.getMongoOperator() + ": " + children + "}";
    }
}
