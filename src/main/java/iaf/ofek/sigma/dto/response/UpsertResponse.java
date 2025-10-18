package iaf.ofek.sigma.dto.response;

import iaf.ofek.sigma.dto.request.WriteRequest;

/**
 * Response for UPSERT operations
 * Contains information about whether document was inserted or updated
 */
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

    public boolean wasInserted() {
        return wasInserted;
    }

    public String getDocumentId() {
        return documentId;
    }

    public long getMatchedCount() {
        return matchedCount;
    }

    public long getModifiedCount() {
        return modifiedCount;
    }
}
