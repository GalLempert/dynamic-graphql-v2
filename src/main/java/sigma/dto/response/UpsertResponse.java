package sigma.dto.response;

import sigma.dto.request.WriteRequest;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * Response for UPSERT operations
 * Contains information about whether document was inserted or updated
 */
@Getter
public class UpsertResponse implements WriteResponse {

    private final boolean wasInserted;
    private final String documentId;
    private final long matchedCount;
    private final long modifiedCount;
    private final List<Map<String, Object>> documents;
    private final String message;

    public UpsertResponse(boolean wasInserted,
                          String documentId,
                          long matchedCount,
                          long modifiedCount,
                          List<Map<String, Object>> documents,
                          String message) {
        this.wasInserted = wasInserted;
        this.documentId = documentId;
        this.matchedCount = matchedCount;
        this.modifiedCount = modifiedCount;
        this.documents = documents;
        this.message = message;
    }

    @Override
    public WriteRequest.WriteType getType() {
        return WriteRequest.WriteType.UPSERT;
    }

    @Override
    public boolean isSuccess() {
        return wasInserted || matchedCount > 0;
    }

    @Override
    public long getAffectedCount() {
        return wasInserted ? 1 : modifiedCount;
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
        return visitor.visitUpsert(this);
    }
}
