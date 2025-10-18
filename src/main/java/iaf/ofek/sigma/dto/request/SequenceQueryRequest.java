package iaf.ofek.sigma.dto.request;

/**
 * Request for sequence-based pagination using Change Streams
 */
public class SequenceQueryRequest implements QueryRequest {

    private final long startSequence;
    private final int bulkSize;

    public SequenceQueryRequest(long startSequence, int bulkSize) {
        this.startSequence = startSequence;
        this.bulkSize = bulkSize;
    }

    public long getStartSequence() {
        return startSequence;
    }

    public int getBulkSize() {
        return bulkSize;
    }

    @Override
    public QueryType getType() {
        return QueryType.SEQUENCE_BASED;
    }

    @Override
    public String toString() {
        return "SequenceQueryRequest{" +
                "startSequence=" + startSequence +
                ", bulkSize=" + bulkSize +
                '}';
    }
}
