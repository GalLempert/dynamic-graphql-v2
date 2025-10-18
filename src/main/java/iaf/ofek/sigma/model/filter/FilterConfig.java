package iaf.ofek.sigma.model.filter;

import java.util.List;
import java.util.Map;

/**
 * Configuration for filters available on a specific endpoint
 * Defines which fields can be filtered and what operators are allowed
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
     */
    public boolean isFieldFilterable(String fieldName) {
        return fieldOperators.containsKey(fieldName);
    }

    /**
     * Checks if an operator is allowed for a given field
     */
    public boolean isOperatorAllowed(String fieldName, FilterOperator operator) {
        List<FilterOperator> allowedOperators = fieldOperators.get(fieldName);
        return allowedOperators != null && allowedOperators.contains(operator);
    }

    /**
     * Gets allowed operators for a field
     */
    public List<FilterOperator> getAllowedOperators(String fieldName) {
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
