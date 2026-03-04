package sigma.service.write;

import sigma.dto.request.WriteRequest;
import sigma.filter.FilterValidator;
import sigma.model.Endpoint;
import sigma.service.schema.SchemaValidator;
import sigma.service.validation.ValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Validates write requests.
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

    public ValidationResult validate(WriteRequest request, Endpoint endpoint) {
        logger.debug("Validating {} request for endpoint: {}", request.getType(), endpoint.getName());

        List<String> errors = new ArrayList<>();

        if (!endpoint.isWriteMethodAllowed(request.getHttpMethod())) {
            errors.add("Write method " + request.getHttpMethod() + " is not allowed for this endpoint");
            return ValidationResult.failure(errors);
        }

        validateFilter(request, endpoint, errors);
        validateSchema(request, endpoint, errors);

        return errors.isEmpty() ? ValidationResult.success() : ValidationResult.failure(errors);
    }

    private void validateFilter(WriteRequest request, Endpoint endpoint, List<String> errors) {
        if (request.getFilter() != null && !request.getFilter().isEmpty()) {
            errors.addAll(filterValidator.validate(request.getFilter(), endpoint.getWriteFilterConfig()));
        }
    }

    private void validateSchema(WriteRequest request, Endpoint endpoint, List<String> errors) {
        if (!endpoint.requiresSchemaValidation()) {
            return;
        }
        List<Map<String, Object>> documentsToValidate = request.getDocumentsForValidation();
        if (documentsToValidate == null || documentsToValidate.isEmpty()) {
            return;
        }
        String schemaName = endpoint.getSchemaReference().getSchemaName();
        ValidationResult schemaResult = schemaValidator.validateBulk(documentsToValidate, schemaName);
        if (!schemaResult.isValid()) {
            errors.addAll(schemaResult.getErrors());
        }
    }
}
