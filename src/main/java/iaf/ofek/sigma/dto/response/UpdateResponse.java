package iaf.ofek.sigma.dto.response;

import iaf.ofek.sigma.dto.request.WriteRequest;
import lombok.Getter;
import org.bson.Document;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Response for UPDATE operations
 * Contains counts of matched and modified documents
 */
@Getter
public class UpdateResponse implements WriteResponse {

    private final long matchedCount;
    private final long modifiedCount;
    private final boolean noOp;
    private final String message;
    private final List<Document> documents;

    public UpdateResponse(long matchedCount, long modifiedCount) {
        this(matchedCount, modifiedCount, false, null, List.of());
    }

    public UpdateResponse(long matchedCount, long modifiedCount, boolean noOp, String message, List<Document> documents) {
        this.matchedCount = matchedCount;
        this.modifiedCount = modifiedCount;
        this.noOp = noOp;
        this.message = message;
        this.documents = documents != null ? Collections.unmodifiableList(documents) : List.of();
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

    public boolean isNoOp() {
        return noOp;
    }

    public Optional<String> getMessage() {
        return Optional.ofNullable(message);
    }

    @Override
    public List<Document> getDocuments() {
        return documents;
    }

    @Override
    public <T> T accept(ResponseVisitor<T> visitor) {
        return visitor.visitUpdate(this);
    }
}
