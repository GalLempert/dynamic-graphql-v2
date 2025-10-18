package iaf.ofek.sigma.dto.request;

import java.util.List;
import java.util.Map;

/**
 * Request to create (insert) new document(s)
 *
 * Supports both single document and bulk insert
 * Audit fields are automatically injected by the system
 */
public class CreateRequest implements WriteRequest {

    private final List<Map<String, Object>> documents;
    private final String requestId;

    public CreateRequest(List<Map<String, Object>> documents, String requestId) {
        this.documents = documents;
        this.requestId = requestId;
    }

    /**
     * Convenience constructor for single document creation
     */
    public CreateRequest(Map<String, Object> document, String requestId) {
        this(List.of(document), requestId);
    }

    @Override
    public WriteType getType() {
        return WriteType.CREATE;
    }

    @Override
    public String getRequestId() {
        return requestId;
    }

    @Override
    public Map<String, Object> getFilter() {
        return null; // CREATE doesn't use filters
    }

    public List<Map<String, Object>> getDocuments() {
        return documents;
    }

    /**
     * Returns true if this is a bulk insert (multiple documents)
     */
    public boolean isBulk() {
        return documents.size() > 1;
    }
}
