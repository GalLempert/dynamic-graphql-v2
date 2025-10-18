package iaf.ofek.sigma.dto.request;

import iaf.ofek.sigma.dto.response.DocumentListResponse;
import iaf.ofek.sigma.dto.response.QueryResponse;
import iaf.ofek.sigma.model.Endpoint;
import iaf.ofek.sigma.model.filter.FilterRequest;
import iaf.ofek.sigma.service.query.QueryBuilder;
import iaf.ofek.sigma.service.query.QueryService;
import iaf.ofek.sigma.service.validation.RequestValidator;
import lombok.Getter;
import org.bson.Document;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;

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
    public Query buildQuery(QueryBuilder queryBuilder) {
        return queryBuilder.getFilterTranslator().translate(filterRequest);
    }

    @Override
    public RequestValidator.ValidationResult validate(RequestValidator validator, Endpoint endpoint) {
        return validator.validateFilteredRequest(this, endpoint);
    }

    @Override
    public QueryResponse execute(QueryService service, String collectionName) {
        Query query = buildQuery(service.getQueryBuilder());
        List<Document> documents = service.getRepository().findWithQuery(collectionName, query);
        return new DocumentListResponse(documents);
    }

    @Override
    public String toString() {
        return "FilteredQueryRequest{" +
                "filterRequest=" + filterRequest +
                '}';
    }
}
