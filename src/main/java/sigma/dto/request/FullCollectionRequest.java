package sigma.dto.request;

import sigma.dto.response.DocumentListResponse;
import sigma.dto.response.QueryResponse;
import sigma.model.Endpoint;
import sigma.service.query.QueryBuilder;
import sigma.service.query.QueryService;
import sigma.service.validation.RequestValidator;
import org.bson.Document;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;

/**
 * Request to retrieve all documents from a collection
 * Represents a simple SELECT * query
 */
public class FullCollectionRequest implements QueryRequest {

    @Override
    public QueryType getType() {
        return QueryType.FULL_COLLECTION;
    }

    @Override
    public Query buildQuery(QueryBuilder queryBuilder) {
        return new Query(); // Empty query = full collection
    }

    @Override
    public RequestValidator.ValidationResult validate(RequestValidator validator, Endpoint endpoint) {
        return RequestValidator.ValidationResult.success(); // Always valid
    }

    @Override
    public QueryResponse execute(QueryService service, String collectionName) {
        List<Document> documents = service.getRepository().findAll(collectionName);
        return new DocumentListResponse(documents);
    }

    @Override
    public String toString() {
        return "FullCollectionRequest{}";
    }
}
