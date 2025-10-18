package iaf.ofek.sigma.model.filter.operator;

import org.springframework.data.mongodb.core.query.Criteria;

/**
 * Strategy interface for filter operators
 * Each operator knows how to apply itself to a MongoDB Criteria
 */
public interface OperatorStrategy {

    /**
     * Applies this operator to a field with the given value
     *
     * @param fieldName The field to apply the operator to
     * @param value The value for the operation
     * @return MongoDB Criteria with the operator applied
     */
    Criteria apply(String fieldName, Object value);

    /**
     * Validates that the value is appropriate for this operator
     *
     * @param value The value to validate
     * @return true if valid, false otherwise
     */
    boolean isValidValue(Object value);

    /**
     * Returns the MongoDB operator symbol (e.g., "$eq", "$gt")
     */
    String getMongoOperator();

    /**
     * Returns true if this is a logical operator ($and, $or, $not, $nor)
     */
    boolean isLogical();
}
