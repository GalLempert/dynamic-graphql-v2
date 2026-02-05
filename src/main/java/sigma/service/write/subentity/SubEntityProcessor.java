package sigma.service.write.subentity;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Coordinates sub-entity processing for write operations.
 */
@Component
public class SubEntityProcessor {

    private final SubEntityIdGenerator idGenerator;
    private final SubEntityCommandFactory commandFactory = new SubEntityCommandFactory();

    public SubEntityProcessor(SubEntityIdGenerator idGenerator) {
        this.idGenerator = idGenerator;
    }

    public Map<String, Object> prepareForCreate(Map<String, Object> document, Set<String> subEntityFields) {
        if (subEntityFields == null || subEntityFields.isEmpty()) {
            return document;
        }
        Map<String, Object> processed = new LinkedHashMap<>(document);
        for (String field : subEntityFields) {
            Object value = processed.get(field);
            if (value == null) {
                continue;
            }
            List<?> payload = ensureList(field, value);
            SubEntityCollection collection = SubEntityCollection.empty(field, idGenerator);
            for (Object item : payload) {
                if (!(item instanceof Map<?, ?> itemMap)) {
                    throw new IllegalArgumentException(
                            "Sub-entity field '" + field + "' must contain JSON objects");
                }
                collection.applyCommand(commandFactory.createForCreate(field, itemMap));
            }
            processed.put(field, collection.toDocuments());
        }
        return processed;
    }

    public void applyUpdateOperations(Map<String, Object> updates,
                                      Set<String> subEntityFields,
                                      boolean updateMultiple,
                                      Supplier<Map<String, Object>> existingDocumentSupplier) {
        if (subEntityFields == null || subEntityFields.isEmpty()) {
            return;
        }
        Set<String> fieldsToProcess = updates.keySet().stream()
                .filter(subEntityFields::contains)
                .collect(Collectors.toSet());
        if (fieldsToProcess.isEmpty()) {
            return;
        }
        if (updateMultiple) {
            throw new IllegalArgumentException("Sub-entity updates require updateMultiple=false");
        }
        Map<String, Object> existingDocument = existingDocumentSupplier.get();
        if (existingDocument == null) {
            throw new IllegalArgumentException("No document found matching filter for sub-entity update");
        }
        for (String field : fieldsToProcess) {
            Object operations = updates.get(field);
            List<?> payload = ensureList(field, operations);
            SubEntityCollection collection = SubEntityCollection.fromExisting(field,
                    existingDocument.get(field), idGenerator);
            for (Object item : payload) {
                if (!(item instanceof Map<?, ?> itemMap)) {
                    throw new IllegalArgumentException(
                            "Sub-entity field '" + field + "' must contain JSON objects");
                }
                collection.applyCommand(commandFactory.createForModify(field, itemMap));
            }
            updates.put(field, collection.toDocuments());
        }
    }

    public Map<String, Object> prepareForUpsertCreate(Map<String, Object> document, Set<String> subEntityFields) {
        Map<String, Object> processed = prepareForCreate(document, subEntityFields);
        processed.put("isDeleted", false);
        return processed;
    }

    public void applyUpsertUpdate(Map<String, Object> updates,
                                  Set<String> subEntityFields,
                                  Map<String, Object> existingDocument) {
        if (existingDocument == null) {
            throw new IllegalArgumentException("Existing document required for sub-entity upsert update");
        }
        applyUpdateOperations(updates, subEntityFields, false, () -> existingDocument);
    }

    private List<?> ensureList(String field, Object value) {
        if (value instanceof List<?> list) {
            return list;
        }
        throw new IllegalArgumentException("Sub-entity field '" + field + "' must be an array");
    }
}
