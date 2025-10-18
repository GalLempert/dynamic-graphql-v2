package iaf.ofek.sigma.model.filter.operator;

import org.springframework.data.mongodb.core.query.Criteria;

/**
 * Less than or equal operator: $lte
 */
public class LessThanEqualOperator extends ComparisonOperator {

    public LessThanEqualOperator() {
        super("$lte");
    }

    @Override
    public Criteria apply(String fieldName, Object value) {
        return Criteria.where(fieldName).lte(value);
    }
}
