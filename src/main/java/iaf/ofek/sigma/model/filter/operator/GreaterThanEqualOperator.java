package iaf.ofek.sigma.model.filter.operator;

import org.springframework.data.mongodb.core.query.Criteria;

/**
 * Greater than or equal operator: $gte
 */
public class GreaterThanEqualOperator extends ComparisonOperator {

    public GreaterThanEqualOperator() {
        super("$gte");
    }

    @Override
    public Criteria apply(String fieldName, Object value) {
        return Criteria.where(fieldName).gte(value);
    }
}
