package sigma.service.write;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Evaluates whether applying a set of update operations will actually change
 * the stored documents.
 *
 * <p>This component encapsulates the "dirty check" logic so the
 * {@link WriteService} can remain focused on orchestration responsibilities.
 * The class clones documents, applies the requested changes and reports whether
 * any functional field value would be modified. This respects the Single
 * Responsibility Principle and keeps the service open for extension.</p>
 */
@Component
public class DocumentChangeDetector {

    /**
     * Evaluates the provided documents against the desired updates.
     *
     * @param existingDocuments Documents currently stored in the database
     * @param updates           Desired updates (field → value)
     * @return Result containing cloned documents with applied changes and a
     * flag indicating whether anything would actually change
     */
    public DocumentChangeResult evaluate(List<Map<String, Object>> existingDocuments, Map<String, Object> updates) {
        if (existingDocuments == null || existingDocuments.isEmpty()) {
            return DocumentChangeResult.noChanges(List.of());
        }

        List<Map<String, Object>> updatedSnapshots = new ArrayList<>(existingDocuments.size());
        boolean anyChanges = false;

        for (Map<String, Object> document : existingDocuments) {
            Map<String, Object> snapshot = cloneDocument(document);
            boolean documentChanged = applyUpdates(snapshot, updates);
            updatedSnapshots.add(snapshot);
            if (documentChanged) {
                anyChanges = true;
            }
        }

        return new DocumentChangeResult(anyChanges, updatedSnapshots);
    }

    private Map<String, Object> cloneDocument(Map<String, Object> source) {
        return source == null ? new LinkedHashMap<>() : new LinkedHashMap<>(source);
    }

    private boolean applyUpdates(Map<String, Object> target, Map<String, Object> updates) {
        if (updates == null || updates.isEmpty()) {
            return false;
        }

        boolean changed = false;
        for (Map.Entry<String, Object> entry : updates.entrySet()) {
            if (applyUpdate(target, entry.getKey(), entry.getValue())) {
                changed = true;
            }
        }
        return changed;
    }

    private boolean applyUpdate(Map<String, Object> target, String key, Object newValue) {
        Object currentValue = readValue(target, key);
        if (Objects.equals(currentValue, newValue)) {
            return false;
        }

        writeValue(target, key, newValue);
        return true;
    }

    private Object readValue(Map<String, Object> document, String key) {
        if (document == null || key == null) {
            return null;
        }

        if (!key.contains(".")) {
            return document.get(key);
        }

        String[] path = key.split("\\.");
        Object current = document;
        for (String segment : path) {
            if (!(current instanceof Map<?, ?> currentMap)) {
                return null;
            }
            current = currentMap.get(segment);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    @SuppressWarnings("unchecked")
    private void writeValue(Map<String, Object> document, String key, Object value) {
        if (document == null || key == null) {
            return;
        }

        if (!key.contains(".")) {
            document.put(key, value);
            return;
        }

        String[] path = key.split("\\.");
        Map<String, Object> current = document;
        for (int i = 0; i < path.length - 1; i++) {
            String segment = path[i];
            Object next = current.get(segment);
            if (!(next instanceof Map)) {
                next = new LinkedHashMap<String, Object>();
                current.put(segment, (Map<String, Object>) next);
            }
            current = (Map<String, Object>) next;
        }

        current.put(path[path.length - 1], value);
    }
}
