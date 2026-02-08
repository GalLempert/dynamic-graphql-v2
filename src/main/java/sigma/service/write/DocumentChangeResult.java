package sigma.service.write;

import java.util.List;
import java.util.Map;

/**
 * Value object describing the outcome of a document dirty-check operation.
 */
public class DocumentChangeResult {

    private final boolean hasChanges;
    private final List<Map<String, Object>> projectedDocuments;

    public DocumentChangeResult(boolean hasChanges, List<Map<String, Object>> projectedDocuments) {
        this.hasChanges = hasChanges;
        this.projectedDocuments = projectedDocuments;
    }

    public static DocumentChangeResult noChanges(List<Map<String, Object>> documents) {
        return new DocumentChangeResult(false, documents);
    }

    public boolean hasChanges() {
        return hasChanges;
    }

    public List<Map<String, Object>> getProjectedDocuments() {
        return projectedDocuments;
    }
}
