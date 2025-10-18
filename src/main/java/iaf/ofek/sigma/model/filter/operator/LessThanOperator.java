package iaf.ofek.sigma.model.filter.operator;

import org.springframework.data.mongodb.core.query.Criteria;

/**
 * Less than operator: $lt
 */
public class LessThanOperator extends ComparisonOperator {

    public LessThanOperator() {
        super("$lt");
    }

    @Override
    public Criteria apply(String fieldName, Object value) {
        return Criteria.where(fieldName).lt(value);
    }
}
