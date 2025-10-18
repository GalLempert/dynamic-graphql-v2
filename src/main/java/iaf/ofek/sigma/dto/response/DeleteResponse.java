package iaf.ofek.sigma.dto.response;

import iaf.ofek.sigma.dto.request.WriteRequest;

/**
 * Response for DELETE operations
 * Contains count of deleted documents
 */
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

    public long getDeletedCount() {
        return deletedCount;
    }
}
