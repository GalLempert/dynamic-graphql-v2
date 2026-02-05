package sigma.model.filter.operator;

import sigma.model.filter.SqlPredicate;
import sigma.model.filter.SqlPredicateFactory;

/**
 * Less than operator: lt
 */
public class LessThanOperator extends ComparisonOperator {

    public LessThanOperator() {
        super("lt");
    }

    @Override
    public SqlPredicate apply(String fieldName, Object value) {
        return SqlPredicateFactory.jsonLessThan(fieldName, value);
    }
}
