package iaf.ofek.sigma.dto.request;

import iaf.ofek.sigma.model.filter.FilterRequest;

/**
 * Request for filtered queries with optional sorting, pagination, and projection
 */
public class FilteredQueryRequest implements QueryRequest {

    private final FilterRequest filterRequest;

    public FilteredQueryRequest(FilterRequest filterRequest) {
        this.filterRequest = filterRequest;
    }

    public FilterRequest getFilterRequest() {
        return filterRequest;
    }

    @Override
    public QueryType getType() {
        return QueryType.FILTERED;
    }

    @Override
    public String toString() {
        return "FilteredQueryRequest{" +
                "filterRequest=" + filterRequest +
                '}';
    }
}
