package iaf.ofek.sigma.dto.response;

import iaf.ofek.sigma.dto.request.WriteRequest;
import lombok.Getter;
import org.bson.Document;

import java.util.Collections;
import java.util.List;

/**
 * Response for DELETE operations
 * Contains count of deleted documents
 */
@Getter
public class DeleteResponse implements WriteResponse {

    private final long deletedCount;
    private final List<Document> documents;

    public DeleteResponse(long deletedCount, List<Document> documents) {
        this.deletedCount = deletedCount;
        this.documents = documents != null ? Collections.unmodifiableList(documents) : List.of();
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
    public List<Document> getDocuments() {
        return documents;
    }

    @Override
    public <T> T accept(ResponseVisitor<T> visitor) {
        return visitor.visitDelete(this);
    }
}
