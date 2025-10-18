package iaf.ofek.sigma.dto.response;

import lombok.Getter;
import org.bson.Document;

import java.util.List;

/**
 * Response containing a list of documents
 */
@Getter
public class DocumentListResponse extends QueryResponse {

    private final List<Document> documents;

    public DocumentListResponse(List<Document> documents) {
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
