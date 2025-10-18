package iaf.ofek.sigma.dto.request;

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
    public String toString() {
        return "FullCollectionRequest{}";
    }
}
