package iaf.ofek.sigma.service.write;

import iaf.ofek.sigma.persistence.repository.DynamicMongoRepository;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Detects whether an UPDATE operation introduces any functional changes.
 *
 * <p>The detector inspects the current state of the documents matched by the
 * update query and compares the requested field assignments with the existing
 * values. If every requested assignment matches the persisted value, the update
 * is considered a no-op and can be safely skipped in order to avoid
 * unnecessary version bumps and audit field updates.</p>
 */
@Component
public class UpdateChangeDetector {

    private static final Logger logger = LoggerFactory.getLogger(UpdateChangeDetector.class);

    private final DynamicMongoRepository repository;

    public UpdateChangeDetector(DynamicMongoRepository repository) {
        this.repository = repository;
    }

    /**
     * Evaluates whether the requested update should be executed or skipped.
     *
     * @param collectionName collection targeted by the update
     * @param query          resolved MongoDB query
     * @param updates        sanitized update payload (functional fields only)
     * @param updateMultiple true when the update targets multiple documents
     * @return evaluation outcome describing if the update should be executed
     */
    public UpdateChangeEvaluation evaluate(String collectionName,
                                           Query query,
                                           Map<String, Object> updates,
                                           boolean updateMultiple) {
        if (updates == null || updates.isEmpty()) {
            logger.debug("No functional updates supplied; skipping update execution");
            return UpdateChangeEvaluation.noOp(Collections.emptyList(), 0,
                    "Update skipped because no functional fields were provided");
        }

        List<Document> matchedDocuments = repository.findWithQuery(collectionName, query);
        int matchedCount = matchedDocuments.size();

        if (matchedCount == 0) {
            logger.debug("No documents matched the update query; proceeding without change detection");
            return UpdateChangeEvaluation.proceed(Collections.emptyList());
        }

        boolean anyChangeRequired = matchedDocuments.stream()
                .anyMatch(document -> hasEffectiveChanges(document, updates));

        if (!anyChangeRequired) {
            String message = updateMultiple
                    ? "Update skipped because all matched documents already contain the requested values"
                    : "Update skipped because the document already contains the requested values";
            logger.info(message + " (collection: {}, matched: {})", collectionName, matchedCount);
            return UpdateChangeEvaluation.noOp(matchedDocuments, matchedCount, message);
        }

        return UpdateChangeEvaluation.proceed(matchedDocuments);
    }

    private boolean hasEffectiveChanges(Document document, Map<String, Object> updates) {
        for (Map.Entry<String, Object> entry : updates.entrySet()) {
            Object currentValue = readValue(document, entry.getKey());
            Object requestedValue = entry.getValue();
            if (!areValuesEqual(currentValue, requestedValue)) {
                return true;
            }
        }
        return false;
    }

    private boolean areValuesEqual(Object currentValue, Object requestedValue) {
        if (currentValue instanceof Number currentNumber && requestedValue instanceof Number requestedNumber) {
            return Double.compare(currentNumber.doubleValue(), requestedNumber.doubleValue()) == 0;
        }
        return Objects.equals(currentValue, requestedValue);
    }

    private Object readValue(Object current, String path) {
        if (current == null || path == null) {
            return null;
        }
        String[] segments = path.split("\\.");
        Object value = current;
        for (String segment : segments) {
            if (value instanceof Document document) {
                value = document.get(segment);
            } else if (value instanceof Map<?, ?> map) {
                value = map.get(segment);
            } else if (value instanceof List<?> list) {
                Integer index = parseIndex(segment);
                if (index == null || index < 0 || index >= list.size()) {
                    return null;
                }
                value = list.get(index);
            } else {
                return null;
            }
        }
        return value;
    }

    private Integer parseIndex(String segment) {
        try {
            return Integer.valueOf(segment);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

}
