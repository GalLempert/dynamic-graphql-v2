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
import java.util.Optional;

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
    public EvaluationResult evaluate(String collectionName,
                                     Query query,
                                     Map<String, Object> updates,
                                     boolean updateMultiple) {
        if (updates == null || updates.isEmpty()) {
            logger.debug("No functional updates supplied; skipping update execution");
            return EvaluationResult.noOp(Collections.emptyList(), 0,
                    "Update skipped because no functional fields were provided");
        }

        List<Document> matchedDocuments = repository.findWithQuery(collectionName, query);
        int matchedCount = matchedDocuments.size();

        if (matchedCount == 0) {
            logger.debug("No documents matched the update query; proceeding without change detection");
            return EvaluationResult.proceed(Collections.emptyList());
        }

        boolean anyChangeRequired = matchedDocuments.stream()
                .anyMatch(document -> hasEffectiveChanges(document, updates));

        if (!anyChangeRequired) {
            String message = updateMultiple
                    ? "Update skipped because all matched documents already contain the requested values"
                    : "Update skipped because the document already contains the requested values";
            logger.info(message + " (collection: {}, matched: {})", collectionName, matchedCount);
            return EvaluationResult.noOp(matchedDocuments, matchedCount, message);
        }

        return EvaluationResult.proceed(matchedDocuments);
    }

    private boolean hasEffectiveChanges(Document document, Map<String, Object> updates) {
        for (Map.Entry<String, Object> entry : updates.entrySet()) {
            ValueLookup currentValueLookup = readValue(document, entry.getKey());
            Object requestedValue = entry.getValue();
            if (!currentValueLookup.exists()) {
                return true;
            }
            if (!areValuesEqual(currentValueLookup.value(), requestedValue)) {
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

    private ValueLookup readValue(Object current, String path) {
        if (current == null || path == null) {
            return ValueLookup.missing();
        }
        String[] segments = path.split("\\.");
        Object value = current;
        for (String segment : segments) {
            if (value instanceof Document document) {
                if (!document.containsKey(segment)) {
                    return ValueLookup.missing();
                }
                value = document.get(segment);
            } else if (value instanceof Map<?, ?> map) {
                if (!map.containsKey(segment)) {
                    return ValueLookup.missing();
                }
                value = map.get(segment);
            } else if (value instanceof List<?> list) {
                Integer index = parseIndex(segment);
                if (index == null || index < 0 || index >= list.size()) {
                    return ValueLookup.missing();
                }
                value = list.get(index);
            } else {
                return ValueLookup.missing();
            }
        }
        return ValueLookup.present(value);
    }

    private Integer parseIndex(String segment) {
        try {
            return Integer.valueOf(segment);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static final class ValueLookup {
        private static final ValueLookup MISSING = new ValueLookup(false, null);

        private final boolean exists;
        private final Object value;

        private ValueLookup(boolean exists, Object value) {
            this.exists = exists;
            this.value = value;
        }

        static ValueLookup missing() {
            return MISSING;
        }

        static ValueLookup present(Object value) {
            return new ValueLookup(true, value);
        }

        boolean exists() {
            return exists;
        }

        Object value() {
            return value;
        }
    }

    /**
     * Result of an update evaluation.
     */
    public static class EvaluationResult {
        private final boolean noOp;
        private final long matchedCount;
        private final String message;
        private final List<Document> matchedDocuments;

        private EvaluationResult(boolean noOp, long matchedCount, String message, List<Document> matchedDocuments) {
            this.noOp = noOp;
            this.matchedCount = matchedCount;
            this.message = message;
            this.matchedDocuments = matchedDocuments != null ? List.copyOf(matchedDocuments) : List.of();
        }

        public static EvaluationResult noOp(List<Document> matchedDocuments, long matchedCount, String message) {
            return new EvaluationResult(true, matchedCount, message, matchedDocuments);
        }

        public static EvaluationResult proceed(List<Document> matchedDocuments) {
            return new EvaluationResult(false, matchedDocuments.size(), null, matchedDocuments);
        }

        public boolean isNoOp() {
            return noOp;
        }

        public long getMatchedCount() {
            return matchedCount;
        }

        public Optional<String> getMessage() {
            return Optional.ofNullable(message);
        }

        public List<Document> getMatchedDocuments() {
            return matchedDocuments;
        }

    }
}
