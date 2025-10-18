package iaf.ofek.sigma.dto.response;

import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * Response for sequence-based queries with pagination metadata
 */
@Getter
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

    @Override
    public String getResponseSizeForLogging() {
        return data.size() + " (sequence)";
    }

    @Override
    public <T> T accept(ResponseVisitor<T> visitor) {
        return visitor.visitSequence(this);
    }
}
