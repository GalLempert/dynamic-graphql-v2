package sigma.model.filter.operator;

import sigma.model.filter.SqlPredicate;
import sigma.model.filter.SqlPredicateFactory;

/**
 * Equality operator: eq
 */
public class EqualOperator extends ComparisonOperator {

    public EqualOperator() {
        super("eq");
    }

    @Override
    public SqlPredicate apply(String fieldName, Object value) {
        return SqlPredicateFactory.jsonEquals(fieldName, value);
    }
}
