package sigma.model.filter.operator;

import sigma.model.filter.SqlPredicate;

/**
 * Type operator: $type
 * In PostgreSQL, uses jsonb_typeof to check the type of a JSONB field
 */
public class TypeOperator extends ComparisonOperator {

    public TypeOperator() {
        super("$type");
    }

    @Override
    public SqlPredicate apply(String fieldName, Object value) {
        return SqlPredicate.jsonbType(fieldName, value);
    }

    @Override
    public boolean isValidValue(Object value) {
        return value instanceof Number || value instanceof String;
    }
}
