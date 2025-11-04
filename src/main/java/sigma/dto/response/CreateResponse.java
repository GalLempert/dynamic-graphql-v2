package sigma.dto.response;

import sigma.dto.request.WriteRequest;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * Response for CREATE operations
 * Contains the IDs of created documents
 */
@Getter
public class CreateResponse implements WriteResponse {

    private final List<String> insertedIds;
    private final long insertedCount;
    private final List<Map<String, Object>> documents;
    private final String message;

    public CreateResponse(List<String> insertedIds, List<Map<String, Object>> documents, String message) {
        this.insertedIds = insertedIds;
        this.insertedCount = insertedIds.size();
        this.documents = documents;
        this.message = message;
    }

    @Override
    public WriteRequest.WriteType getType() {
        return WriteRequest.WriteType.CREATE;
    }

    @Override
    public boolean isSuccess() {
        return insertedCount > 0;
    }

    @Override
    public long getAffectedCount() {
        return insertedCount;
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
        return visitor.visitCreate(this);
    }
}
