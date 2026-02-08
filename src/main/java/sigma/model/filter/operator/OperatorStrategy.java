package sigma.model.filter.operator;

import sigma.model.filter.SqlPredicate;

/**
 * Strategy interface for filter operators
 * Each operator knows how to apply itself to generate SQL predicates for PostgreSQL JSONB
 */
public interface OperatorStrategy {

    /**
     * Applies this operator to a field with the given value
     * Generates a SQL predicate for PostgreSQL JSONB queries
     *
     * @param fieldName The field to apply the operator to
     * @param value The value for the operation
     * @return SqlPredicate with the SQL fragment and parameters
     */
    SqlPredicate apply(String fieldName, Object value);

    /**
     * Validates that the value is appropriate for this operator
     *
     * @param value The value to validate
     * @return true if valid, false otherwise
     */
    boolean isValidValue(Object value);

    /**
     * Returns the operator symbol (e.g., "eq", "gt")
     */
    String getOperator();

    /**
     * Returns true if this is a logical operator (and, or, not, nor)
     */
    boolean isLogical();
}
