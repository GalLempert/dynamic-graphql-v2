package sigma.model.filter.operator;

import sigma.model.filter.SqlPredicate;

import java.util.List;

/**
 * In array operator: $in
 */
public class InOperator extends ComparisonOperator {

    public InOperator() {
        super("$in");
    }

    @Override
    public SqlPredicate apply(String fieldName, Object value) {
        if (value instanceof List) {
            return SqlPredicate.jsonbIn(fieldName, (List<?>) value);
        }
        throw new IllegalArgumentException("$in operator requires a list value");
    }

    @Override
    public boolean isValidValue(Object value) {
        return value instanceof List;
    }
}
