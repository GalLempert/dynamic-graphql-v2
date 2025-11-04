package sigma.service.write;

import org.bson.Document;

import java.util.List;

/**
 * Value object describing the outcome of a document dirty-check operation.
 */
public class DocumentChangeResult {

    private final boolean hasChanges;
    private final List<Document> projectedDocuments;

    public DocumentChangeResult(boolean hasChanges, List<Document> projectedDocuments) {
        this.hasChanges = hasChanges;
        this.projectedDocuments = projectedDocuments;
    }

    public static DocumentChangeResult noChanges(List<Document> documents) {
        return new DocumentChangeResult(false, documents);
    }

    public boolean hasChanges() {
        return hasChanges;
    }

    public List<Document> getProjectedDocuments() {
        return projectedDocuments;
    }
}

