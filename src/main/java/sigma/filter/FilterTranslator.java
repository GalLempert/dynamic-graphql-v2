package sigma.filter;

import sigma.model.filter.FilterRequest;
import sigma.model.filter.FilterResult;
import sigma.model.filter.SqlPredicate;
import sigma.model.filter.node.FieldFilterNode;
import sigma.model.filter.node.FilterNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Translates filter requests into PostgreSQL query components using OOP approach
 */
@Component
public class FilterTranslator {

    private static final Logger logger = LoggerFactory.getLogger(FilterTranslator.class);
    private final FilterParser parser;

    public FilterTranslator(FilterParser parser) {
        this.parser = parser;
    }

    /**
     * Translates a FilterRequest into a FilterResult for PostgreSQL queries
     */
    public FilterResult translate(FilterRequest filterRequest) {
        FilterResult.Builder builder = FilterResult.builder();

        // Parse and translate filter criteria
        if (filterRequest.getFilter() != null && !filterRequest.getFilter().isEmpty()) {
            FilterNode filterTree = parser.parse(filterRequest.getFilter());
            SqlPredicate predicate = filterTree.toPredicate();
            builder.whereClause(predicate.getSql());
            builder.addParameters(predicate.getParameters());
        }

        // Apply options
        if (filterRequest.getOptions() != null) {
            applyOptions(builder, filterRequest.getOptions());
        }

        FilterResult result = builder.build();
        logger.debug("Translated filter request to PostgreSQL query: {}", result);
        return result;
    }

    /**
     * Translates GET request parameters into a simple FilterResult
     * Supports: ?field=value&field2=value2
     */
    public FilterResult translateGetParameters(Map<String, String> params) {
        FilterResult.Builder builder = FilterResult.builder();
        List<SqlPredicate> predicates = new ArrayList<>();

        for (Map.Entry<String, String> entry : params.entrySet()) {
            String field = entry.getKey();
            String value = entry.getValue();

            // Skip special parameters
            if (isSpecialParameter(field)) {
                continue;
            }

            // Create field filter node and convert to predicate
            FieldFilterNode fieldNode = new FieldFilterNode(field, value);
            predicates.add(fieldNode.toPredicate());
        }

        if (!predicates.isEmpty()) {
            SqlPredicate combined = SqlPredicate.and(predicates.toArray(new SqlPredicate[0]));
            builder.whereClause(combined.getSql());
            builder.addParameters(combined.getParameters());
        }

        // Apply pagination/sorting from special parameters
        if (params.containsKey("limit")) {
            builder.limit(Integer.parseInt(params.get("limit")));
        }
        if (params.containsKey("skip")) {
            builder.offset(Integer.parseInt(params.get("skip")));
        }
        if (params.containsKey("sort")) {
            String sortField = params.get("sort");
            String direction = "ASC";
            if (sortField.startsWith("-")) {
                direction = "DESC";
                sortField = sortField.substring(1);
            }
            builder.orderByClause(buildOrderByForField(sortField, direction));
        }

        FilterResult result = builder.build();
        logger.debug("Translated GET parameters to query: {}", result);
        return result;
    }

    /**
     * Applies filter options (sort, limit, skip) to the builder
     */
    private void applyOptions(FilterResult.Builder builder, FilterRequest.FilterOptions options) {
        // Apply sorting
        if (options.getSort() != null && !options.getSort().isEmpty()) {
            StringBuilder orderBy = new StringBuilder();
            boolean first = true;
            for (Map.Entry<String, Integer> entry : options.getSort().entrySet()) {
                String field = entry.getKey();
                Integer direction = entry.getValue();
                String sortDirection = direction < 0 ? "DESC" : "ASC";

                if (!first) {
                    orderBy.append(", ");
                }
                orderBy.append(buildOrderByForField(field, sortDirection));
                first = false;
            }
            builder.orderByClause(orderBy.toString());
        }

        // Apply limit
        if (options.getLimit() != null && options.getLimit() > 0) {
            builder.limit(options.getLimit());
        }

        // Apply skip
        if (options.getSkip() != null && options.getSkip() > 0) {
            builder.offset(options.getSkip());
        }
    }

    /**
     * Builds an ORDER BY fragment for a single field.
     * For _id, uses the id column directly.
     * For other fields, uses JSONB path expression.
     */
    private String buildOrderByForField(String field, String direction) {
        if ("_id".equals(field)) {
            return "d.id " + direction;
        }
        // For JSONB fields, we sort by the text value
        return "d.dynamicFields->>'" + escapeJsonKey(field) + "' " + direction;
    }

    /**
     * Escapes a JSON key for use in PostgreSQL JSONB path expressions
     */
    private String escapeJsonKey(String key) {
        return key.replace("'", "''").replace("\\", "\\\\");
    }

    /**
     * Checks if a parameter is a special parameter (not a filter field)
     */
    private boolean isSpecialParameter(String param) {
        return param.equals("limit") || param.equals("skip") || param.equals("sort") ||
               param.equals("sequence") || param.equals("bulkSize");
    }
}
