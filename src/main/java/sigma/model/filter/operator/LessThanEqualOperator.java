package sigma.model.filter.operator;

import sigma.model.filter.SqlPredicate;

/**
 * Less than or equal operator: $lte
 */
public class LessThanEqualOperator extends ComparisonOperator {

    public LessThanEqualOperator() {
        super("$lte");
    }

    @Override
    public SqlPredicate apply(String fieldName, Object value) {
        return SqlPredicate.jsonbLessThanOrEqual(fieldName, value);
    }
}
