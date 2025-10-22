package iaf.ofek.sigma.dto.response;

import iaf.ofek.sigma.dto.request.WriteRequest;
import lombok.Getter;
import org.bson.Document;

import java.util.Collections;
import java.util.List;

/**
 * Response for CREATE operations
 * Contains the IDs of created documents
 */
@Getter
public class CreateResponse implements WriteResponse {

    private final List<String> insertedIds;
    private final long insertedCount;
    private final List<Document> documents;

    public CreateResponse(List<String> insertedIds, List<Document> documents) {
        this.insertedIds = insertedIds;
        this.insertedCount = insertedIds.size();
        this.documents = documents != null ? Collections.unmodifiableList(documents) : List.of();
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
    public List<Document> getDocuments() {
        return documents;
    }

    @Override
    public <T> T accept(ResponseVisitor<T> visitor) {
        return visitor.visitCreate(this);
    }
}
