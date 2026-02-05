package sigma.model.filter.node;

import sigma.model.filter.FilterConfig;
import sigma.model.filter.SqlPredicate;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Represents a composite filter with multiple field conditions
 * This is used when a filter map contains multiple keys (implicitly ANDed)
 * Example: { "category": "electronics", "price": { "gt": 100 } }
 */
public class CompositeFilterNode extends FilterNode {

    private final List<FilterNode> children;

    public CompositeFilterNode(List<FilterNode> children) {
        this.children = children;
    }

    @Override
    public SqlPredicate toPredicate() {
        if (children.isEmpty()) {
            return new SqlPredicate("1=1");
        }

        if (children.size() == 1) {
            return children.get(0).toPredicate();
        }

        // Multiple children are implicitly ANDed
        List<SqlPredicate> predicates = children.stream()
                .map(FilterNode::toPredicate)
                .collect(Collectors.toList());

        return SqlPredicate.and(predicates.toArray(new SqlPredicate[0]));
    }

    @Override
    public List<String> validate(FilterConfig config) {
        List<String> errors = new ArrayList<>();

        for (FilterNode child : children) {
            errors.addAll(child.validate(config));
        }

        return errors;
    }

    public List<FilterNode> getChildren() {
        return children;
    }

    @Override
    public String toString() {
        return "CompositeFilter{" + children + "}";
    }
}
