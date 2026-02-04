package sigma.model.filter.operator;

import sigma.model.filter.SqlPredicate;

/**
 * Not equal operator: $ne
 */
public class NotEqualOperator extends ComparisonOperator {

    public NotEqualOperator() {
        super("$ne");
    }

    @Override
    public SqlPredicate apply(String fieldName, Object value) {
        return SqlPredicate.jsonbNotEquals(fieldName, value);
    }
}
