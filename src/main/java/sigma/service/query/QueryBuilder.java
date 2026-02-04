package sigma.service.query;

import sigma.dto.request.FilteredQueryRequest;
import sigma.dto.request.QueryRequest;
import sigma.filter.FilterTranslator;
import sigma.model.filter.FilterResult;
import org.springframework.stereotype.Service;

/**
 * Builds FilterResult objects from QueryRequest objects for PostgreSQL
 * Single Responsibility: Query translation
 */
@Service
public class QueryBuilder {

    private final FilterTranslator filterTranslator;

    public QueryBuilder(FilterTranslator filterTranslator) {
        this.filterTranslator = filterTranslator;
    }

    public FilterTranslator getFilterTranslator() {
        return filterTranslator;
    }

    /**
     * Builds a FilterResult from a QueryRequest
     * Uses polymorphism - ZERO switch statements!
     *
     * @param request The request to translate
     * @return FilterResult (null for sequence-based queries)
     */
    public FilterResult build(QueryRequest request) {
        return request.buildQuery(this);
    }

    /**
     * Builds a filter result for full collection retrieval
     */
    private FilterResult buildFullCollectionQuery() {
        return FilterResult.builder().build();
    }

    /**
     * Builds a filter result for filtered requests
     */
    private FilterResult buildFilteredQuery(FilteredQueryRequest request) {
        return filterTranslator.translate(request.getFilterRequest());
    }
}
