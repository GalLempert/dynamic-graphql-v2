package iaf.ofek.sigma.service.write;

import iaf.ofek.sigma.dto.request.CreateRequest;
import iaf.ofek.sigma.dto.request.WriteRequest;
import iaf.ofek.sigma.filter.FilterValidator;
import iaf.ofek.sigma.model.Endpoint;
import iaf.ofek.sigma.service.schema.SchemaValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates write requests
 *
 * Responsibilities:
 * - Validate that write method is allowed for endpoint
 * - Validate filters (if present)
 * - Validate documents against JSON Schema (if configured)
 */
@Service
public class WriteValidator {

    private static final Logger logger = LoggerFactory.getLogger(WriteValidator.class);

    private final FilterValidator filterValidator;
    private final SchemaValidator schemaValidator;

    public WriteValidator(FilterValidator filterValidator, SchemaValidator schemaValidator) {
        this.filterValidator = filterValidator;
        this.schemaValidator = schemaValidator;
    }

    /**
     * Validates a write request
     */
    public ValidationResult validate(WriteRequest request, Endpoint endpoint) {
        logger.debug("Validating {} request for endpoint: {}", request.getType(), endpoint.getName());

        List<String> errors = new ArrayList<>();

        // 1. Validate write method is allowed
        String writeMethod = mapWriteTypeToHttpMethod(request.getType());
        if (!endpoint.isWriteMethodAllowed(writeMethod)) {
            errors.add("Write method " + writeMethod + " is not allowed for this endpoint");
            return ValidationResult.failure(errors);
        }

        // 2. Validate filter (if present)
        if (request.getFilter() != null && !request.getFilter().isEmpty()) {
            List<String> filterErrors = filterValidator.validate(
                    request.getFilter(),
                    endpoint.getFilterConfig()
            );
            errors.addAll(filterErrors);
        }

        // 3. Validate schema (if required)
        if (endpoint.requiresSchemaValidation()) {
            String schemaName = endpoint.getSchemaReference().getSchemaName();

            if (request instanceof CreateRequest createRequest) {
                // Validate all documents
                SchemaValidator.ValidationResult schemaResult = schemaValidator.validateBulk(
                        createRequest.getDocuments(),
                        schemaName
                );
                if (!schemaResult.isValid()) {
                    errors.addAll(schemaResult.getErrors());
                }
            } else if (request.getType() == WriteRequest.WriteType.UPSERT) {
                // Validate upsert document
                var upsertRequest = (iaf.ofek.sigma.dto.request.UpsertRequest) request;
                SchemaValidator.ValidationResult schemaResult = schemaValidator.validate(
                        upsertRequest.getDocument(),
                        schemaName
                );
                if (!schemaResult.isValid()) {
                    errors.addAll(schemaResult.getErrors());
                }
            }
            // Note: UPDATE doesn't need full schema validation as it's a partial update
        }

        if (errors.isEmpty()) {
            return ValidationResult.success();
        } else {
            return ValidationResult.failure(errors);
        }
    }

    /**
     * Maps write type to HTTP method
     */
    private String mapWriteTypeToHttpMethod(WriteRequest.WriteType writeType) {
        return switch (writeType) {
            case CREATE -> "POST";
            case UPDATE -> "PATCH";
            case DELETE -> "DELETE";
            case UPSERT -> "PUT";
        };
    }

    /**
     * Validation result
     */
    public static class ValidationResult {
        private final boolean valid;
        private final List<String> errors;

        private ValidationResult(boolean valid, List<String> errors) {
            this.valid = valid;
            this.errors = errors;
        }

        public static ValidationResult success() {
            return new ValidationResult(true, List.of());
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
    }
}
