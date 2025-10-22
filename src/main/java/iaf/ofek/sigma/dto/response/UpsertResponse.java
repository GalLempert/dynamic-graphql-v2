package iaf.ofek.sigma.dto.response;

import iaf.ofek.sigma.dto.request.WriteRequest;
import lombok.Getter;
import org.bson.Document;

import java.util.Collections;
import java.util.List;

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
    private final List<Document> documents;

    public UpsertResponse(boolean wasInserted,
                          String documentId,
                          long matchedCount,
                          long modifiedCount,
                          List<Document> documents) {
        this.wasInserted = wasInserted;
        this.documentId = documentId;
        this.matchedCount = matchedCount;
        this.modifiedCount = modifiedCount;
        this.documents = documents != null ? Collections.unmodifiableList(documents) : List.of();
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
    public List<Document> getDocuments() {
        return documents;
    }

    @Override
    public <T> T accept(ResponseVisitor<T> visitor) {
        return visitor.visitUpsert(this);
    }
}
