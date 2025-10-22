package sigma.dto.response;

import sigma.dto.request.WriteRequest;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * Response for UPDATE operations
 * Contains counts of matched and modified documents
 */
@Getter
public class UpdateResponse implements WriteResponse {

    private final long matchedCount;
    private final long modifiedCount;
    private final List<Map<String, Object>> documents;
    private final String message;

    public UpdateResponse(long matchedCount, long modifiedCount,
                          List<Map<String, Object>> documents,
                          String message) {
        this.matchedCount = matchedCount;
        this.modifiedCount = modifiedCount;
        this.documents = documents;
        this.message = message;
    }

    @Override
    public WriteRequest.WriteType getType() {
        return WriteRequest.WriteType.UPDATE;
    }

    @Override
    public boolean isSuccess() {
        return matchedCount > 0;
    }

    @Override
    public long getAffectedCount() {
        return modifiedCount;
    }

    @Override
    public List<Map<String, Object>> getDocuments() {
        return documents;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public <T> T accept(ResponseVisitor<T> visitor) {
        return visitor.visitUpdate(this);
    }
}
