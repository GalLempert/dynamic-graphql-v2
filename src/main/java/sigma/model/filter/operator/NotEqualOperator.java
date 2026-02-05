package sigma.model.filter.operator;

import sigma.model.filter.SqlPredicate;
import sigma.model.filter.SqlPredicateFactory;

/**
 * Not equal operator: ne
 */
public class NotEqualOperator extends ComparisonOperator {

    public NotEqualOperator() {
        super("ne");
    }

    @Override
    public SqlPredicate apply(String fieldName, Object value) {
        return SqlPredicateFactory.jsonNotEquals(fieldName, value);
    }
}
