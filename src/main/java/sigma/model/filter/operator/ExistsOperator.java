package sigma.model.filter.operator;

import org.springframework.data.mongodb.core.query.Criteria;

/**
 * Exists operator: $exists
 */
public class ExistsOperator extends ComparisonOperator {

    public ExistsOperator() {
        super("$exists");
    }

    @Override
    public Criteria apply(String fieldName, Object value) {
        if (value instanceof Boolean) {
            return Criteria.where(fieldName).exists((Boolean) value);
        }
        throw new IllegalArgumentException("$exists operator requires a boolean value");
    }

    @Override
    public boolean isValidValue(Object value) {
        return value instanceof Boolean;
    }
}
