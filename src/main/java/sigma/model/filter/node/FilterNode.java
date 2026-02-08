package sigma.model.filter.node;

import sigma.model.filter.FilterConfig;
import sigma.model.filter.SqlPredicate;

import java.util.List;

/**
 * Abstract base class for filter tree nodes
 * Represents a node in the filter expression tree
 */
public abstract class FilterNode {

    /**
     * Converts this node to a SQL predicate for PostgreSQL JSONB queries
     */
    public abstract SqlPredicate toPredicate();

    /**
     * Validates this node against the filter configuration
     *
     * @param config Filter configuration from ZooKeeper
     * @return List of validation errors (empty if valid)
     */
    public abstract List<String> validate(FilterConfig config);

    /**
     * Returns a string representation of this node (for debugging)
     */
    @Override
    public abstract String toString();
}
