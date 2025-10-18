package iaf.ofek.sigma.model.filter.operator;

import org.springframework.data.mongodb.core.query.Criteria;

/**
 * Type operator: $type
 */
public class TypeOperator extends ComparisonOperator {

    public TypeOperator() {
        super("$type");
    }

    @Override
    public Criteria apply(String fieldName, Object value) {
        if (value instanceof Number) {
            return Criteria.where(fieldName).type(((Number) value).intValue());
        }
        throw new IllegalArgumentException("$type operator requires a numeric value");
    }

    @Override
    public boolean isValidValue(Object value) {
        return value instanceof Number;
    }
}
