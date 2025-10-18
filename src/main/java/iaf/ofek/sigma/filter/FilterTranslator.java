package iaf.ofek.sigma.filter;

import iaf.ofek.sigma.model.filter.FilterRequest;
import iaf.ofek.sigma.model.filter.node.FieldFilterNode;
import iaf.ofek.sigma.model.filter.node.FilterNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Translates filter requests into MongoDB queries using OOP approach
 */
@Component
public class FilterTranslator {

    private static final Logger logger = LoggerFactory.getLogger(FilterTranslator.class);
    private final FilterParser parser;

    public FilterTranslator(FilterParser parser) {
        this.parser = parser;
    }

    /**
     * Translates a FilterRequest into a MongoDB Query object
     */
    public Query translate(FilterRequest filterRequest) {
        Query query = new Query();

        // Parse and translate filter criteria
        if (filterRequest.getFilter() != null && !filterRequest.getFilter().isEmpty()) {
            FilterNode filterTree = parser.parse(filterRequest.getFilter());
            Criteria criteria = filterTree.toCriteria();
            query.addCriteria(criteria);
        }

        // Apply options
        if (filterRequest.getOptions() != null) {
            applyOptions(query, filterRequest.getOptions());
        }

        logger.debug("Translated filter request to MongoDB query: {}", query);
        return query;
    }

    /**
     * Translates GET request parameters into a simple query
     * Supports: ?field=value&field2=value2
     */
    public Query translateGetParameters(Map<String, String> params) {
        Query query = new Query();

        for (Map.Entry<String, String> entry : params.entrySet()) {
            String field = entry.getKey();
            String value = entry.getValue();

            // Skip special parameters
            if (isSpecialParameter(field)) {
                continue;
            }

            // Create field filter node and convert to criteria
            FieldFilterNode fieldNode = new FieldFilterNode(field, value);
            query.addCriteria(fieldNode.toCriteria());
        }

        // Apply pagination/sorting from special parameters
        if (params.containsKey("limit")) {
            query.limit(Integer.parseInt(params.get("limit")));
        }
        if (params.containsKey("skip")) {
            query.skip(Integer.parseInt(params.get("skip")));
        }
        if (params.containsKey("sort")) {
            String sortField = params.get("sort");
            Sort.Direction direction = Sort.Direction.ASC;
            if (sortField.startsWith("-")) {
                direction = Sort.Direction.DESC;
                sortField = sortField.substring(1);
            }
            query.with(Sort.by(direction, sortField));
        }

        logger.debug("Translated GET parameters to query: {}", query);
        return query;
    }

    /**
     * Applies filter options (sort, limit, skip, projection) to the query
     */
    private void applyOptions(Query query, FilterRequest.FilterOptions options) {
        // Apply sorting
        if (options.getSort() != null && !options.getSort().isEmpty()) {
            List<Sort.Order> orders = new ArrayList<>();
            for (Map.Entry<String, Integer> entry : options.getSort().entrySet()) {
                String field = entry.getKey();
                Integer direction = entry.getValue();
                Sort.Direction sortDirection = direction < 0 ? Sort.Direction.DESC : Sort.Direction.ASC;
                orders.add(new Sort.Order(sortDirection, field));
            }
            query.with(Sort.by(orders));
        }

        // Apply limit
        if (options.getLimit() != null && options.getLimit() > 0) {
            query.limit(options.getLimit());
        }

        // Apply skip
        if (options.getSkip() != null && options.getSkip() > 0) {
            query.skip(options.getSkip());
        }

        // Apply projection
        if (options.getProjection() != null && !options.getProjection().isEmpty()) {
            for (Map.Entry<String, Integer> entry : options.getProjection().entrySet()) {
                String field = entry.getKey();
                Integer include = entry.getValue();
                if (include == 1) {
                    query.fields().include(field);
                } else if (include == 0) {
                    query.fields().exclude(field);
                }
            }
        }
    }

    /**
     * Checks if a parameter is a special parameter (not a filter field)
     */
    private boolean isSpecialParameter(String param) {
        return param.equals("limit") || param.equals("skip") || param.equals("sort") ||
               param.equals("sequence") || param.equals("bulkSize");
    }
}
