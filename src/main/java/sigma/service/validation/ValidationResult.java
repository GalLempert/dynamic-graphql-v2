package sigma.service.validation;

import java.util.List;

/**
 * Shared, immutable result of a validation operation.
 * Used across request validation, schema validation, and write validation.
 */
public record ValidationResult(boolean valid, List<String> errors) {

    private static final ValidationResult SUCCESS = new ValidationResult(true, List.of());

    public ValidationResult {
        errors = errors != null ? List.copyOf(errors) : List.of();
    }

    public static ValidationResult success() {
        return SUCCESS;
    }

    public static ValidationResult failure(List<String> errors) {
        return new ValidationResult(false, errors);
    }

    public boolean isValid() {
        return valid;
    }

    public List<String> getErrors() {
        return errors;
    }

    public String getErrorMessage() {
        return errors.isEmpty() ? null : String.join("; ", errors);
    }
}
