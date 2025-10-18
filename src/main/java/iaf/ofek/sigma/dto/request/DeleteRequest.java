package iaf.ofek.sigma.dto.request;

import java.util.Map;

/**
 * Request to delete document(s)
 *
 * Features:
 * - Filter determines which documents to delete
 * - Can delete single document (filter by _id) or multiple (filter by other fields)
 * - Supports soft delete if needed (would update a field instead of actual deletion)
 */
public class DeleteRequest implements WriteRequest {

    private final Map<String, Object> filter;
    private final String requestId;
    private final boolean deleteMultiple;

    public DeleteRequest(Map<String, Object> filter,
                        String requestId,
                        boolean deleteMultiple) {
        this.filter = filter;
        this.requestId = requestId;
        this.deleteMultiple = deleteMultiple;
    }

    @Override
    public WriteType getType() {
        return WriteType.DELETE;
    }

    @Override
    public String getRequestId() {
        return requestId;
    }

    @Override
    public Map<String, Object> getFilter() {
        return filter;
    }

    public boolean isDeleteMultiple() {
        return deleteMultiple;
    }
}
