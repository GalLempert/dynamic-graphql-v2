package iaf.ofek.sigma.dto.response;

import iaf.ofek.sigma.dto.request.WriteRequest;
import lombok.Getter;

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

    public UpsertResponse(boolean wasInserted, String documentId, long matchedCount, long modifiedCount) {
        this.wasInserted = wasInserted;
        this.documentId = documentId;
        this.matchedCount = matchedCount;
        this.modifiedCount = modifiedCount;
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
    public <T> T accept(ResponseVisitor<T> visitor) {
        return visitor.visitUpsert(this);
    }
}
