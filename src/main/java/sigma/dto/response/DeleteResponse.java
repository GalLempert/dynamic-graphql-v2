package sigma.dto.response;

import sigma.dto.request.WriteRequest;
import lombok.Getter;

/**
 * Response for DELETE operations
 * Contains count of deleted documents
 */
@Getter
public class DeleteResponse implements WriteResponse {

    private final long deletedCount;

    public DeleteResponse(long deletedCount) {
        this.deletedCount = deletedCount;
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
    public <T> T accept(ResponseVisitor<T> visitor) {
        return visitor.visitDelete(this);
    }
}
