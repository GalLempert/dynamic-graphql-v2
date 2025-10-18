package iaf.ofek.sigma.dto.request;

import lombok.Getter;

import java.util.List;
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
@Getter
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

    /**
     * Returns document for schema validation (polymorphic OOP approach)
     */
    @Override
    public List<Map<String, Object>> getDocumentsForValidation() {
        return List.of(document);
    }

    @Override
    public iaf.ofek.sigma.dto.response.WriteResponse execute(
            iaf.ofek.sigma.service.write.WriteService service,
            String collectionName) {
        return service.executeUpsert(this, collectionName);
    }

    @Override
    public String getHttpMethod() {
        return "PUT";
    }
}
