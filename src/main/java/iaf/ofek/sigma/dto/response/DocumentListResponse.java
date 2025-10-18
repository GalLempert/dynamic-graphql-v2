package iaf.ofek.sigma.dto.response;

import org.bson.Document;

import java.util.List;

/**
 * Response containing a list of documents
 */
public class DocumentListResponse extends QueryResponse {

    private final List<Document> documents;

    public DocumentListResponse(List<Document> documents) {
        super(true, null);
        this.documents = documents;
    }

    public List<Document> getDocuments() {
        return documents;
    }

    public int getCount() {
        return documents != null ? documents.size() : 0;
    }
}
