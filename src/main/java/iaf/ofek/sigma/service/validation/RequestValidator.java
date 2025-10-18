package iaf.ofek.sigma.service.validation;

import iaf.ofek.sigma.dto.request.FilteredQueryRequest;
import iaf.ofek.sigma.dto.request.QueryRequest;
import iaf.ofek.sigma.dto.request.SequenceQueryRequest;
import iaf.ofek.sigma.filter.FilterValidator;
import iaf.ofek.sigma.model.Endpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates query requests against endpoint configurations
 * Single Responsibility: Request validation
 * Can be reused across different controllers/entry points
 */
@Service
public class RequestValidator {

    private static final Logger logger = LoggerFactory.getLogger(RequestValidator.class);
    private final FilterValidator filterValidator;

    public RequestValidator(FilterValidator filterValidator) {
        this.filterValidator = filterValidator;
    }

    /**
     * Validates a query request
     * Uses polymorphism - ZERO switch statements!
     *
     * @param request The request to validate
     * @param endpoint The endpoint configuration
     * @return Validation result with errors if any
     */
    public ValidationResult validate(QueryRequest request, Endpoint endpoint) {
        logger.debug("Validating request of type: {}", request.getType());
        return request.validate(this, endpoint);
    }

    /**
     * Validates a sequence-based request
     * Made public for Template Method pattern
     */
    public ValidationResult validateSequenceRequest(SequenceQueryRequest request, Endpoint endpoint) {
        List<String> errors = new ArrayList<>();

        // Check if sequence queries are enabled
        if (!endpoint.isSequenceEnabled()) {
            errors.add("Sequence queries are not enabled for this endpoint");
            return ValidationResult.failure(errors);
        }

        // Validate sequence value
        if (request.getStartSequence() < 0) {
            errors.add("Sequence must be non-negative");
        }

        // Validate bulk size
        if (request.getBulkSize() <= 0) {
            errors.add("Bulk size must be positive");
        }

        if (request.getBulkSize() > 10000) {
            errors.add("Bulk size exceeds maximum allowed (10000)");
        }

        return errors.isEmpty() ? ValidationResult.success() : ValidationResult.failure(errors);
    }

    /**
     * Validates a filtered request
     * Made public for Template Method pattern
     */
    public ValidationResult validateFilteredRequest(FilteredQueryRequest request, Endpoint endpoint) {
        // If no filter, it's valid
        if (request.getFilterRequest() == null ||
            request.getFilterRequest().getFilter() == null ||
            request.getFilterRequest().getFilter().isEmpty()) {
            return ValidationResult.success();
        }

        // Validate filter against configuration
        List<String> errors = filterValidator.validate(
                request.getFilterRequest().getFilter(),
                endpoint.getFilterConfig()
        );

        return errors.isEmpty() ? ValidationResult.success() : ValidationResult.failure(errors);
    }

    /**
     * Result of validation
     */
    public static class ValidationResult {
        private final boolean valid;
        private final List<String> errors;

        private ValidationResult(boolean valid, List<String> errors) {
            this.valid = valid;
            this.errors = errors != null ? errors : new ArrayList<>();
        }

        public static ValidationResult success() {
            return new ValidationResult(true, null);
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
}
