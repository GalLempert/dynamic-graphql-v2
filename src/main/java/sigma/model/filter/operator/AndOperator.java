package sigma.model.filter.operator;

import org.springframework.data.mongodb.core.query.Criteria;

import java.util.List;

/**
 * Logical AND operator: $and
 */
public class AndOperator extends LogicalOperator {

    public AndOperator() {
        super("$and");
    }

    @Override
    public Criteria applyCriteria(List<Criteria> criteriaList) {
        if (criteriaList.isEmpty()) {
            return new Criteria();
        }
        return new Criteria().andOperator(criteriaList.toArray(new Criteria[0]));
    }
}
