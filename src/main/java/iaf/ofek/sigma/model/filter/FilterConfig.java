package iaf.ofek.sigma.model.filter;

import iaf.ofek.sigma.model.AuditFields;

import java.util.List;
import java.util.Map;

/**
 * Configuration for filters available on a specific endpoint
 * Defines which fields can be filtered and what operators are allowed
 *
 * NOTE: Primary key (_id) is ALWAYS filterable with $eq operator,
 * regardless of configuration. This ensures single document operations
 * are always possible.
 */
public class FilterConfig {

    private final Map<String, List<FilterOperator>> fieldOperators;
    private final boolean enabled;

    public FilterConfig(Map<String, List<FilterOperator>> fieldOperators, boolean enabled) {
        this.fieldOperators = fieldOperators;
        this.enabled = enabled;
    }

    public Map<String, List<FilterOperator>> getFieldOperators() {
        return fieldOperators;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Checks if a field is filterable
     * PRIMARY KEY (_id) is ALWAYS filterable
     */
    public boolean isFieldFilterable(String fieldName) {
        // Primary key is always filterable
        if (AuditFields.PRIMARY_KEY.equals(fieldName)) {
            return true;
        }
        return fieldOperators.containsKey(fieldName);
    }

    /**
     * Checks if an operator is allowed for a given field
     * PRIMARY KEY (_id) always allows $eq operator
     */
    public boolean isOperatorAllowed(String fieldName, FilterOperator operator) {
        // Primary key always allows $eq (equals) operator
        if (AuditFields.PRIMARY_KEY.equals(fieldName) && operator == FilterOperator.EQ) {
            return true;
        }

        List<FilterOperator> allowedOperators = fieldOperators.get(fieldName);
        return allowedOperators != null && allowedOperators.contains(operator);
    }

    /**
     * Gets allowed operators for a field
     * PRIMARY KEY (_id) always returns $eq operator
     */
    public List<FilterOperator> getAllowedOperators(String fieldName) {
        // Primary key always allows $eq operator
        if (AuditFields.PRIMARY_KEY.equals(fieldName)) {
            return List.of(FilterOperator.EQ);
        }
        return fieldOperators.get(fieldName);
    }

    @Override
    public String toString() {
        return "FilterConfig{" +
                "fieldOperators=" + fieldOperators +
                ", enabled=" + enabled +
                '}';
    }
}
