package sigma.model.filter.operator;

import org.springframework.data.mongodb.core.query.Criteria;

import java.util.List;

/**
 * Logical NOR operator: $nor
 */
public class NorOperator extends LogicalOperator {

    public NorOperator() {
        super("$nor");
    }

    @Override
    public Criteria applyCriteria(List<Criteria> criteriaList) {
        if (criteriaList.isEmpty()) {
            return new Criteria();
        }
        return new Criteria().norOperator(criteriaList.toArray(new Criteria[0]));
    }
}
