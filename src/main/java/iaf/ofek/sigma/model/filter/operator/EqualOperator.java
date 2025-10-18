package iaf.ofek.sigma.model.filter.operator;

import org.springframework.data.mongodb.core.query.Criteria;

/**
 * Equality operator: $eq
 */
public class EqualOperator extends ComparisonOperator {

    public EqualOperator() {
        super("$eq");
    }

    @Override
    public Criteria apply(String fieldName, Object value) {
        return Criteria.where(fieldName).is(value);
    }
}
