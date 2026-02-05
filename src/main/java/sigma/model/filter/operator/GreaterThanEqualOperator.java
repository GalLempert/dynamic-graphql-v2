package sigma.model.filter.operator;

import sigma.model.filter.SqlPredicate;
import sigma.model.filter.SqlPredicateFactory;

/**
 * Greater than or equal operator: gte
 */
public class GreaterThanEqualOperator extends ComparisonOperator {

    public GreaterThanEqualOperator() {
        super("gte");
    }

    @Override
    public SqlPredicate apply(String fieldName, Object value) {
        return SqlPredicateFactory.jsonGreaterThanOrEqual(fieldName, value);
    }
}
