package iaf.ofek.sigma.model.filter.operator;

import org.springframework.data.mongodb.core.query.Criteria;

import java.util.List;

/**
 * In array operator: $in
 */
public class InOperator extends ComparisonOperator {

    public InOperator() {
        super("$in");
    }

    @Override
    public Criteria apply(String fieldName, Object value) {
        if (value instanceof List) {
            return Criteria.where(fieldName).in((List<?>) value);
        }
        throw new IllegalArgumentException("$in operator requires a list value");
    }

    @Override
    public boolean isValidValue(Object value) {
        return value instanceof List;
    }
}
