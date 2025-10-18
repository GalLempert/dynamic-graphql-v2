package iaf.ofek.sigma.dto.response;

import iaf.ofek.sigma.dto.request.WriteRequest;
import lombok.Getter;

import java.util.List;

/**
 * Response for CREATE operations
 * Contains the IDs of created documents
 */
@Getter
public class CreateResponse implements WriteResponse {

    private final List<String> insertedIds;
    private final long insertedCount;

    public CreateResponse(List<String> insertedIds) {
        this.insertedIds = insertedIds;
        this.insertedCount = insertedIds.size();
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
    public <T> T accept(ResponseVisitor<T> visitor) {
        return visitor.visitCreate(this);
    }
}
