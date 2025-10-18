package iaf.ofek.sigma.model.filter.operator;

import org.springframework.data.mongodb.core.query.Criteria;

import java.util.List;

/**
 * Logical OR operator: $or
 */
public class OrOperator extends LogicalOperator {

    public OrOperator() {
        super("$or");
    }

    @Override
    public Criteria applyCriteria(List<Criteria> criteriaList) {
        if (criteriaList.isEmpty()) {
            return new Criteria();
        }
        return new Criteria().orOperator(criteriaList.toArray(new Criteria[0]));
    }
}
