package sigma.model.filter.operator;

import sigma.model.filter.SqlPredicate;
import sigma.model.filter.SqlPredicateFactory;

/**
 * Greater than operator: gt
 */
public class GreaterThanOperator extends ComparisonOperator {

    public GreaterThanOperator() {
        super("gt");
    }

    @Override
    public SqlPredicate apply(String fieldName, Object value) {
        return SqlPredicateFactory.jsonGreaterThan(fieldName, value);
    }
}
