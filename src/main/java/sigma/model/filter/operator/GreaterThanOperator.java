package sigma.model.filter.operator;

import sigma.model.filter.SqlPredicate;

/**
 * Greater than operator: $gt
 */
public class GreaterThanOperator extends ComparisonOperator {

    public GreaterThanOperator() {
        super("$gt");
    }

    @Override
    public SqlPredicate apply(String fieldName, Object value) {
        return SqlPredicate.jsonbGreaterThan(fieldName, value);
    }
}
