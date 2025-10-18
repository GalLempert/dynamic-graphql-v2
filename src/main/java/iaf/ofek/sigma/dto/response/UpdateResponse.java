package iaf.ofek.sigma.dto.response;

import iaf.ofek.sigma.dto.request.WriteRequest;

/**
 * Response for UPDATE operations
 * Contains counts of matched and modified documents
 */
public class UpdateResponse implements WriteResponse {

    private final long matchedCount;
    private final long modifiedCount;

    public UpdateResponse(long matchedCount, long modifiedCount) {
        this.matchedCount = matchedCount;
        this.modifiedCount = modifiedCount;
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

    public long getMatchedCount() {
        return matchedCount;
    }

    public long getModifiedCount() {
        return modifiedCount;
    }
}
