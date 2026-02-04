package sigma.model.filter.operator;

import sigma.model.filter.SqlPredicate;

/**
 * Base class for comparison operators
 */
public abstract class ComparisonOperator implements OperatorStrategy {

    private final String operator;

    protected ComparisonOperator(String operator) {
        this.operator = operator;
    }

    @Override
    public String getOperator() {
        return operator;
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
