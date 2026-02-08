package sigma.model.filter.operator;

import sigma.model.filter.SqlPredicate;
import sigma.model.filter.SqlPredicateFactory;

/**
 * Exists operator: exists
 */
public class ExistsOperator extends ComparisonOperator {

    public ExistsOperator() {
        super("exists");
    }

    @Override
    public SqlPredicate apply(String fieldName, Object value) {
        boolean shouldExist = true;
        if (value instanceof Boolean) {
            shouldExist = (Boolean) value;
        }
        return SqlPredicateFactory.jsonExists(fieldName, shouldExist);
    }

    @Override
    public boolean isValidValue(Object value) {
        return value instanceof Boolean;
    }
}
