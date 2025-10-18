package iaf.ofek.sigma.service.query;

import iaf.ofek.sigma.dto.request.FilteredQueryRequest;
import iaf.ofek.sigma.dto.request.FullCollectionRequest;
import iaf.ofek.sigma.dto.request.QueryRequest;
import iaf.ofek.sigma.dto.request.SequenceQueryRequest;
import iaf.ofek.sigma.filter.FilterTranslator;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

/**
 * Builds MongoDB Query objects from QueryRequest objects
 * Single Responsibility: Query translation
 */
@Service
public class QueryBuilder {

    private final FilterTranslator filterTranslator;

    public QueryBuilder(FilterTranslator filterTranslator) {
        this.filterTranslator = filterTranslator;
    }

    /**
     * Builds a MongoDB Query from a QueryRequest
     *
     * @param request The request to translate
     * @return MongoDB Query object (null for sequence-based queries)
     */
    public Query build(QueryRequest request) {
        return switch (request.getType()) {
            case FILTERED -> buildFilteredQuery((FilteredQueryRequest) request);
            case FULL_COLLECTION -> buildFullCollectionQuery();
            case SEQUENCE_BASED -> null; // Sequence queries don't use Query objects
        };
    }

    /**
     * Builds a query for full collection retrieval
     */
    private Query buildFullCollectionQuery() {
        return new Query();
    }

    /**
     * Builds a query for filtered requests
     */
    private Query buildFilteredQuery(FilteredQueryRequest request) {
        return filterTranslator.translate(request.getFilterRequest());
    }
}
