package iaf.ofek.sigma.model.filter.operator;

import org.springframework.data.mongodb.core.query.Criteria;

/**
 * Greater than operator: $gt
 */
public class GreaterThanOperator extends ComparisonOperator {

    public GreaterThanOperator() {
        super("$gt");
    }

    @Override
    public Criteria apply(String fieldName, Object value) {
        return Criteria.where(fieldName).gt(value);
    }
}
