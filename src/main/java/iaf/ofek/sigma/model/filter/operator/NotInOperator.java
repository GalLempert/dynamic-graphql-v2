package iaf.ofek.sigma.model.filter.operator;

import org.springframework.data.mongodb.core.query.Criteria;

import java.util.List;

/**
 * Not in array operator: $nin
 */
public class NotInOperator extends ComparisonOperator {

    public NotInOperator() {
        super("$nin");
    }

    @Override
    public Criteria apply(String fieldName, Object value) {
        if (value instanceof List) {
            return Criteria.where(fieldName).nin((List<?>) value);
        }
        throw new IllegalArgumentException("$nin operator requires a list value");
    }

    @Override
    public boolean isValidValue(Object value) {
        return value instanceof List;
    }
}
