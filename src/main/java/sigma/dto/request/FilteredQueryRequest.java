package sigma.dto.request;

import sigma.dto.response.DocumentListResponse;
import sigma.dto.response.QueryResponse;
import sigma.model.Endpoint;
import sigma.model.filter.FilterRequest;
import sigma.model.filter.FilterResult;
import sigma.service.query.QueryBuilder;
import sigma.service.query.QueryService;
import sigma.service.validation.RequestValidator;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * Request for filtered queries with optional sorting, pagination, and projection
 */
@Getter
public class FilteredQueryRequest implements QueryRequest {

    private final FilterRequest filterRequest;

    public FilteredQueryRequest(FilterRequest filterRequest) {
        this.filterRequest = filterRequest;
    }

    @Override
    public QueryType getType() {
        return QueryType.FILTERED;
    }

    @Override
    public FilterResult buildQuery(QueryBuilder queryBuilder) {
        return queryBuilder.getFilterTranslator().translate(filterRequest);
    }

    @Override
    public RequestValidator.ValidationResult validate(RequestValidator validator, Endpoint endpoint) {
        return validator.validateFilteredRequest(this, endpoint);
    }

    @Override
    public QueryResponse execute(QueryService service, String collectionName) {
        FilterResult filterResult = buildQuery(service.getQueryBuilder());
        List<Map<String, Object>> documents = service.getRepository().findWithQuery(
                collectionName,
                filterResult.getWhereClause(),
                filterResult.getOrderByClause(),
                filterResult.getLimit(),
                filterResult.getOffset(),
                filterResult.getParameters()
        );
        return new DocumentListResponse(documents);
    }

    @Override
    public String toString() {
        return "FilteredQueryRequest{" +
                "filterRequest=" + filterRequest +
                '}';
    }
}
