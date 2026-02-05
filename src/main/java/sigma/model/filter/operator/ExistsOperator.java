package sigma.model.filter.operator;

import sigma.model.filter.SqlPredicate;

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
        return SqlPredicate.jsonbExists(fieldName, shouldExist);
    }

    @Override
    public boolean isValidValue(Object value) {
        return value instanceof Boolean;
    }
}
