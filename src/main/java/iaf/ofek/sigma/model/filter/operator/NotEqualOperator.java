package iaf.ofek.sigma.model.filter.operator;

import org.springframework.data.mongodb.core.query.Criteria;

/**
 * Not equal operator: $ne
 */
public class NotEqualOperator extends ComparisonOperator {

    public NotEqualOperator() {
        super("$ne");
    }

    @Override
    public Criteria apply(String fieldName, Object value) {
        return Criteria.where(fieldName).ne(value);
    }
}
