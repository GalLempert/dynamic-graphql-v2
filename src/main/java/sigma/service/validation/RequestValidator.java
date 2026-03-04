package sigma.service.validation;

import sigma.dto.request.FilteredQueryRequest;
import sigma.dto.request.QueryRequest;
import sigma.dto.request.SequenceQueryRequest;
import sigma.filter.FilterValidator;
import sigma.model.Endpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates query requests against endpoint configurations.
 * Single Responsibility: Request validation.
 * Can be reused across different controllers/entry points.
 */
@Service
public class RequestValidator {

    private static final Logger logger = LoggerFactory.getLogger(RequestValidator.class);
    private final FilterValidator filterValidator;

    public RequestValidator(FilterValidator filterValidator) {
        this.filterValidator = filterValidator;
    }

    public ValidationResult validate(QueryRequest request, Endpoint endpoint) {
        logger.debug("Validating request of type: {}", request.getType());
        return request.validate(this, endpoint);
    }

    public ValidationResult validateSequenceRequest(SequenceQueryRequest request, Endpoint endpoint) {
        List<String> errors = new ArrayList<>();

        if (!endpoint.isSequenceEnabled()) {
            errors.add("Sequence queries are not enabled for this endpoint");
            return ValidationResult.failure(errors);
        }
        if (request.getStartSequence() < 0) {
            errors.add("Sequence must be non-negative");
        }
        if (request.getBulkSize() <= 0) {
            errors.add("Bulk size must be positive");
        }
        if (request.getBulkSize() > 10000) {
            errors.add("Bulk size exceeds maximum allowed (10000)");
        }

        return errors.isEmpty() ? ValidationResult.success() : ValidationResult.failure(errors);
    }

    public ValidationResult validateFilteredRequest(FilteredQueryRequest request, Endpoint endpoint) {
        if (request.getFilterRequest() == null ||
            request.getFilterRequest().getFilter() == null ||
            request.getFilterRequest().getFilter().isEmpty()) {
            return ValidationResult.success();
        }

        List<String> errors = filterValidator.validate(
                request.getFilterRequest().getFilter(),
                endpoint.getReadFilterConfig()
        );

        return errors.isEmpty() ? ValidationResult.success() : ValidationResult.failure(errors);
    }
}
