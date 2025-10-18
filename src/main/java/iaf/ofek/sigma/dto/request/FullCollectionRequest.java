package iaf.ofek.sigma.dto.request;

import iaf.ofek.sigma.dto.response.DocumentListResponse;
import iaf.ofek.sigma.dto.response.QueryResponse;
import iaf.ofek.sigma.model.Endpoint;
import iaf.ofek.sigma.service.query.QueryBuilder;
import iaf.ofek.sigma.service.query.QueryService;
import iaf.ofek.sigma.service.validation.RequestValidator;
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
