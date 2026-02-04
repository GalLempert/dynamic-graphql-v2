package sigma.dto.request;

import sigma.dto.response.DocumentListResponse;
import sigma.dto.response.QueryResponse;
import sigma.model.Endpoint;
import sigma.model.filter.FilterResult;
import sigma.service.query.QueryBuilder;
import sigma.service.query.QueryService;
import sigma.service.validation.RequestValidator;

import java.util.List;
import java.util.Map;

/**
 * Request to retrieve all documents from a table
 * Represents a simple SELECT * query
 */
public class FullCollectionRequest implements QueryRequest {

    @Override
    public QueryType getType() {
        return QueryType.FULL_COLLECTION;
    }

    @Override
    public FilterResult buildQuery(QueryBuilder queryBuilder) {
        return FilterResult.builder().build(); // Empty filter = full collection
    }

    @Override
    public RequestValidator.ValidationResult validate(RequestValidator validator, Endpoint endpoint) {
        return RequestValidator.ValidationResult.success(); // Always valid
    }

    @Override
    public QueryResponse execute(QueryService service, String collectionName) {
        List<Map<String, Object>> documents = service.getRepository().findAll(collectionName);
        return new DocumentListResponse(documents);
    }

    @Override
    public String toString() {
        return "FullCollectionRequest{}";
    }
}
