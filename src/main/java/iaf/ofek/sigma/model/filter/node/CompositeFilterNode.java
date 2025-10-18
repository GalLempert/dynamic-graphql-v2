package iaf.ofek.sigma.model.filter.node;

import iaf.ofek.sigma.model.filter.FilterConfig;
import org.springframework.data.mongodb.core.query.Criteria;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Represents a composite filter with multiple field conditions
 * This is used when a filter map contains multiple keys (implicitly ANDed)
 * Example: { "category": "electronics", "price": { "$gt": 100 } }
 */
public class CompositeFilterNode extends FilterNode {

    private final List<FilterNode> children;

    public CompositeFilterNode(List<FilterNode> children) {
        this.children = children;
    }

    @Override
    public Criteria toCriteria() {
        if (children.isEmpty()) {
            return new Criteria();
        }

        if (children.size() == 1) {
            return children.get(0).toCriteria();
        }

        // Multiple children are implicitly ANDed
        List<Criteria> criteriaList = children.stream()
                .map(FilterNode::toCriteria)
                .collect(Collectors.toList());

        return new Criteria().andOperator(criteriaList.toArray(new Criteria[0]));
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
