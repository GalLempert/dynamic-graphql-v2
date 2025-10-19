package iaf.ofek.sigma.dto.request;

import lombok.Getter;

import java.util.Map;

/**
 * Request to update existing document(s)
 *
 * Features:
 * - Filter determines which documents to update
 * - Only provided fields are updated (partial update)
 * - Audit fields are automatically updated by the system
 * - Can update single document (filter by _id) or multiple (filter by other fields)
 */
@Getter
public class UpdateRequest implements WriteRequest {

    private final Map<String, Object> filter;
    private final Map<String, Object> updates;
    private final String requestId;
    private final boolean updateMultiple;

    public UpdateRequest(Map<String, Object> filter,
                        Map<String, Object> updates,
                        String requestId,
                        boolean updateMultiple) {
        this.filter = filter;
        this.updates = updates;
        this.requestId = requestId;
        this.updateMultiple = updateMultiple;
    }

    @Override
    public WriteType getType() {
        return WriteType.UPDATE;
    }

    @Override
    public iaf.ofek.sigma.dto.response.WriteResponse execute(
            iaf.ofek.sigma.service.write.WriteService service,
            iaf.ofek.sigma.model.Endpoint endpoint) {
        return service.executeUpdate(this, endpoint);
    }

    @Override
    public String getHttpMethod() {
        return "PATCH";
    }
}
