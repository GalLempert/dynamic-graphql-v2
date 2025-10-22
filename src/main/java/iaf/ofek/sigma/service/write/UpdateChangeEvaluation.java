package iaf.ofek.sigma.service.write;

import org.bson.Document;

import java.util.List;
import java.util.Optional;

/**
 * Immutable evaluation result describing whether an update should proceed
 * or be skipped as a no-op.
 */
public record UpdateChangeEvaluation(boolean noOp,
                                     long matchedCount,
                                     String message,
                                     List<Document> documents) {

    public UpdateChangeEvaluation {
        documents = documents == null ? List.of() : List.copyOf(documents);
    }

    public static UpdateChangeEvaluation noOp(List<Document> documents,
                                              long matchedCount,
                                              String message) {
        return new UpdateChangeEvaluation(true, matchedCount, message, documents);
    }

    public static UpdateChangeEvaluation proceed(List<Document> documents) {
        long matched = documents == null ? 0 : documents.size();
        return new UpdateChangeEvaluation(false, matched, null, documents);
    }

    public boolean isNoOp() {
        return noOp;
    }

    public Optional<String> message() {
        return Optional.ofNullable(message);
    }
}
