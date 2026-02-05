package sigma.model.filter.operator;

import sigma.model.filter.SqlPredicate;

/**
 * Equality operator: eq
 */
public class EqualOperator extends ComparisonOperator {

    public EqualOperator() {
        super("eq");
    }

    @Override
    public SqlPredicate apply(String fieldName, Object value) {
        return SqlPredicate.jsonbEquals(fieldName, value);
    }
}
