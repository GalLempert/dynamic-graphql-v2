package iaf.ofek.sigma.model.filter.operator;

import org.springframework.data.mongodb.core.query.Criteria;

/**
 * Regex operator: $regex
 */
public class RegexOperator extends ComparisonOperator {

    public RegexOperator() {
        super("$regex");
    }

    @Override
    public Criteria apply(String fieldName, Object value) {
        return Criteria.where(fieldName).regex(value.toString());
    }
}
