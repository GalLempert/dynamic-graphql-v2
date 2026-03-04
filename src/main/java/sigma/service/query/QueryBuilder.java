package sigma.service.query;

import sigma.dto.request.QueryRequest;
import sigma.filter.FilterTranslator;
import sigma.model.filter.FilterResult;
import org.springframework.stereotype.Service;

/**
 * Builds FilterResult objects from QueryRequest objects.
 * Delegates to polymorphic dispatch on QueryRequest implementations.
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

    public FilterResult build(QueryRequest request) {
        return request.buildQuery(this);
    }
}
