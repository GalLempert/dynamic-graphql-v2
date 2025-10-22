package sigma.model.filter.operator;

import org.springframework.data.mongodb.core.query.Criteria;

import java.util.List;

/**
 * Logical NOT operator: $not
 * Note: MongoDB's $not works differently - it negates a single expression
 */
public class NotOperator extends LogicalOperator {

    public NotOperator() {
        super("$not");
    }

    @Override
    public Criteria applyCriteria(List<Criteria> criteriaList) {
        if (criteriaList.isEmpty()) {
            return new Criteria();
        }
        if (criteriaList.size() > 1) {
            throw new IllegalArgumentException("$not operator only accepts a single condition");
        }
        return criteriaList.get(0).not();
    }
}
