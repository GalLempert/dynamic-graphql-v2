package sigma.model.filter.operator;

import sigma.model.filter.SqlPredicate;

import java.util.List;

/**
 * Base class for logical operators
 */
public abstract class LogicalOperator implements OperatorStrategy {

    private final String operator;

    protected LogicalOperator(String operator) {
        this.operator = operator;
    }

    @Override
    public String getOperator() {
        return operator;
    }

    @Override
    public boolean isLogical() {
        return true;
    }

    /**
     * Logical operators work on lists of predicates, not field values
     * This method should not be called directly
     */
    @Override
    public SqlPredicate apply(String fieldName, Object value) {
        throw new UnsupportedOperationException(
            "Logical operators should use applyPredicates() instead of apply()");
    }

    /**
     * Applies the logical operator to a list of SQL predicates
     *
     * @param predicates List of predicates to combine
     * @return Combined SqlPredicate
     */
    public abstract SqlPredicate applyPredicates(List<SqlPredicate> predicates);

    @Override
    public boolean isValidValue(Object value) {
        return value instanceof List;
    }
}
