package iaf.ofek.sigma.filter;

import iaf.ofek.sigma.model.filter.FilterConfig;
import iaf.ofek.sigma.model.filter.node.FilterNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Validates filter requests against endpoint filter configurations
 * Uses OOP approach with FilterNode tree
 */
@Component
public class FilterValidator {

    private static final Logger logger = LoggerFactory.getLogger(FilterValidator.class);
    private final FilterParser parser;

    public FilterValidator(FilterParser parser) {
        this.parser = parser;
    }

    /**
     * Validates a filter map against the filter configuration
     * Returns a list of validation errors (empty if valid)
     */
    public List<String> validate(Map<String, Object> filter, FilterConfig config) {
        List<String> errors = new ArrayList<>();

        if (filter == null || filter.isEmpty()) {
            return errors; // Empty filter is valid
        }

        if (!config.isEnabled()) {
            errors.add("Filtering is not enabled for this endpoint");
            return errors;
        }

        try {
            // Parse the filter into a tree
            FilterNode filterTree = parser.parse(filter);

            // Validate the tree
            errors.addAll(filterTree.validate(config));

        } catch (IllegalArgumentException e) {
            errors.add("Invalid filter structure: " + e.getMessage());
            logger.error("Filter parsing error", e);
        } catch (Exception e) {
            errors.add("Unexpected error during validation: " + e.getMessage());
            logger.error("Unexpected validation error", e);
        }

        return errors;
    }
}
