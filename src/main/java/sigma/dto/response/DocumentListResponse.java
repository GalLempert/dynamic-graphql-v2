package sigma.dto.response;

import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * Response containing a list of documents
 */
@Getter
public class DocumentListResponse extends QueryResponse {

    private final List<Map<String, Object>> documents;

    public DocumentListResponse(List<Map<String, Object>> documents) {
        super(true, null);
        this.documents = documents;
    }

    public int getCount() {
        return documents != null ? documents.size() : 0;
    }

    @Override
    public String getResponseSizeForLogging() {
        return String.valueOf(getCount());
    }

    @Override
    public <T> T accept(ResponseVisitor<T> visitor) {
        return visitor.visitDocumentList(this);
    }
}
