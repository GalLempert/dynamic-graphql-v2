package sigma.dto.request;

import sigma.dto.response.QueryResponse;
import sigma.dto.response.SequenceResponse;
import sigma.model.Endpoint;
import sigma.model.filter.FilterResult;
import sigma.service.query.QueryBuilder;
import sigma.service.query.QueryService;
import sigma.service.validation.RequestValidator;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * Request for sequence-based pagination
 */
@Getter
public class SequenceQueryRequest implements QueryRequest {

    private final long startSequence;
    private final int bulkSize;

    public SequenceQueryRequest(long startSequence, int bulkSize) {
        this.startSequence = startSequence;
        this.bulkSize = bulkSize;
    }

    @Override
    public QueryType getType() {
        return QueryType.SEQUENCE_BASED;
    }

    @Override
    public FilterResult buildQuery(QueryBuilder queryBuilder) {
        return null; // Sequence queries don't use FilterResult
    }

    @Override
    public RequestValidator.ValidationResult validate(RequestValidator validator, Endpoint endpoint) {
        return validator.validateSequenceRequest(this, endpoint);
    }

    @Override
    public QueryResponse execute(QueryService service, String collectionName) {
        Map<String, Object> result = service.getRepository()
                .getNextPageBySequence(collectionName, startSequence, bulkSize);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) result.get("data");
        long nextSequence = (Long) result.get("nextSequence");
        boolean hasMore = (Boolean) result.get("hasMore");

        return new SequenceResponse(nextSequence, data, hasMore);
    }

    @Override
    public String toString() {
        return "SequenceQueryRequest{" +
                "startSequence=" + startSequence +
                ", bulkSize=" + bulkSize +
                '}';
    }
}
