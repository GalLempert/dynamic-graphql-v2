package iaf.ofek.sigma.dto.response;

import java.util.List;
import java.util.Map;

/**
 * Response for sequence-based queries with pagination metadata
 */
public class SequenceResponse extends QueryResponse {

    private final long nextSequence;
    private final List<Map<String, Object>> data;
    private final boolean hasMore;

    public SequenceResponse(long nextSequence, List<Map<String, Object>> data, boolean hasMore) {
        super(true, null);
        this.nextSequence = nextSequence;
        this.data = data;
        this.hasMore = hasMore;
    }

    public long getNextSequence() {
        return nextSequence;
    }

    public List<Map<String, Object>> getData() {
        return data;
    }

    public boolean isHasMore() {
        return hasMore;
    }
}
