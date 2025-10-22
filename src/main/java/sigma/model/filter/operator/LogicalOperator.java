package sigma.model.filter.operator;

import org.springframework.data.mongodb.core.query.Criteria;

import java.util.List;

/**
 * Base class for logical operators
 */
public abstract class LogicalOperator implements OperatorStrategy {

    private final String mongoOperator;

    protected LogicalOperator(String mongoOperator) {
        this.mongoOperator = mongoOperator;
    }

    @Override
    public String getMongoOperator() {
        return mongoOperator;
    }

    @Override
    public boolean isLogical() {
        return true;
    }

    /**
     * Logical operators work on lists of criteria, not field values
     * This method should not be called directly
     */
    @Override
    public Criteria apply(String fieldName, Object value) {
        throw new UnsupportedOperationException(
            "Logical operators should use applyCriteria() instead of apply()");
    }

    /**
     * Applies the logical operator to a list of criteria
     *
     * @param criteriaList List of criteria to combine
     * @return Combined criteria
     */
    public abstract Criteria applyCriteria(List<Criteria> criteriaList);

    @Override
    public boolean isValidValue(Object value) {
        return value instanceof List;
    }
}
