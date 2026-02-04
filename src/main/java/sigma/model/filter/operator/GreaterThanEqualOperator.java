package sigma.model.filter.operator;

import sigma.model.filter.SqlPredicate;

/**
 * Greater than or equal operator: $gte
 */
public class GreaterThanEqualOperator extends ComparisonOperator {

    public GreaterThanEqualOperator() {
        super("$gte");
    }

    @Override
    public SqlPredicate apply(String fieldName, Object value) {
        return SqlPredicate.jsonbGreaterThanOrEqual(fieldName, value);
    }
}
