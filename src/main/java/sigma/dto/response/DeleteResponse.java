package sigma.dto.response;

import sigma.dto.request.WriteRequest;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * Response for DELETE operations
 * Contains count of deleted documents
 */
@Getter
public class DeleteResponse implements WriteResponse {

    private final long deletedCount;
    private final List<Map<String, Object>> documents;
    private final String message;

    public DeleteResponse(long deletedCount, List<Map<String, Object>> documents, String message) {
        this.deletedCount = deletedCount;
        this.documents = documents;
        this.message = message;
    }

    @Override
    public WriteRequest.WriteType getType() {
        return WriteRequest.WriteType.DELETE;
    }

    @Override
    public boolean isSuccess() {
        return deletedCount > 0;
    }

    @Override
    public long getAffectedCount() {
        return deletedCount;
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
        return visitor.visitDelete(this);
    }
}
