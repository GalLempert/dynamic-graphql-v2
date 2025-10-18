package iaf.ofek.sigma.dto.request;

import java.util.Map;

/**
 * Request to upsert (update or insert) a document
 *
 * Behavior:
 * - If document matching filter exists: UPDATE it with provided data
 * - If no document matches filter: INSERT new document with provided data
 *
 * Use cases:
 * - Idempotent operations
 * - "Save" semantics (don't care if exists or not)
 * - External ID synchronization
 */
public class UpsertRequest implements WriteRequest {

    private final Map<String, Object> filter;
    private final Map<String, Object> document;
    private final String requestId;

    public UpsertRequest(Map<String, Object> filter,
                        Map<String, Object> document,
                        String requestId) {
        this.filter = filter;
        this.document = document;
        this.requestId = requestId;
    }

    @Override
    public WriteType getType() {
        return WriteType.UPSERT;
    }

    @Override
    public String getRequestId() {
        return requestId;
    }

    @Override
    public Map<String, Object> getFilter() {
        return filter;
    }

    public Map<String, Object> getDocument() {
        return document;
    }
}
