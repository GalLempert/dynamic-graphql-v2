package sigma.model.filter.operator;

import sigma.model.filter.SqlPredicate;

import java.util.List;

/**
 * Not in array operator: nin
 */
public class NotInOperator extends ComparisonOperator {

    public NotInOperator() {
        super("nin");
    }

    @Override
    public SqlPredicate apply(String fieldName, Object value) {
        if (value instanceof List) {
            return SqlPredicate.jsonbNotIn(fieldName, (List<?>) value);
        }
        throw new IllegalArgumentException("nin operator requires a list value");
    }

    @Override
    public boolean isValidValue(Object value) {
        return value instanceof List;
    }
}
