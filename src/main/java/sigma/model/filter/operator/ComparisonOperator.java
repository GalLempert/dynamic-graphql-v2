package sigma.model.filter.operator;

import org.springframework.data.mongodb.core.query.Criteria;

/**
 * Base class for comparison operators
 */
public abstract class ComparisonOperator implements OperatorStrategy {

    private final String mongoOperator;

    protected ComparisonOperator(String mongoOperator) {
        this.mongoOperator = mongoOperator;
    }

    @Override
    public String getMongoOperator() {
        return mongoOperator;
    }

    @Override
    public boolean isLogical() {
        return false;
    }

    @Override
    public boolean isValidValue(Object value) {
        return value != null;
    }
}
