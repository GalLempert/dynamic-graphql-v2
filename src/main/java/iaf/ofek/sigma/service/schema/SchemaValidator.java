package iaf.ofek.sigma.service.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import iaf.ofek.sigma.model.schema.JsonSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validates documents against JSON Schemas
 *
 * Uses the networknt JSON Schema validator library
 * Supports JSON Schema Draft 2020-12
 */
@Service
public class SchemaValidator {

    private static final Logger logger = LoggerFactory.getLogger(SchemaValidator.class);

    private final SchemaManager schemaManager;
    private final ObjectMapper objectMapper;
    private final JsonSchemaFactory schemaFactory;

    public SchemaValidator(SchemaManager schemaManager, ObjectMapper objectMapper) {
        this.schemaManager = schemaManager;
        this.objectMapper = objectMapper;
        this.schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
    }

    /**
     * Validates a document against a schema
     *
     * @param document The document to validate
     * @param schemaName Name of the schema to validate against
     * @return Validation result with success/failure and error messages
     */
    public ValidationResult validate(Map<String, Object> document, String schemaName) {
        logger.debug("Validating document against schema: {}", schemaName);

        // 1. Get schema
        JsonSchema jsonSchema = schemaManager.getSchema(schemaName);
        if (jsonSchema == null) {
            logger.error("Schema '{}' not found", schemaName);
            return ValidationResult.failure(List.of("Schema '" + schemaName + "' not found"));
        }

        // 2. Convert document to JsonNode
        JsonNode documentNode = objectMapper.valueToTree(document);

        // 3. Create validator and validate
        com.networknt.schema.JsonSchema validator = schemaFactory.getSchema(jsonSchema.getSchema());
        Set<ValidationMessage> errors = validator.validate(documentNode);

        // 4. Convert errors to strings
        if (errors.isEmpty()) {
            logger.debug("Document passed validation against schema: {}", schemaName);
            return ValidationResult.success();
        } else {
            List<String> errorMessages = errors.stream()
                    .map(ValidationMessage::getMessage)
                    .toList();
            logger.warn("Document failed validation against schema '{}': {}", schemaName, errorMessages);
            return ValidationResult.failure(errorMessages);
        }
    }

    /**
     * Validates multiple documents against a schema (bulk validation)
     *
     * @param documents List of documents to validate
     * @param schemaName Name of the schema to validate against
     * @return Validation result with success/failure and error messages
     */
    public ValidationResult validateBulk(List<Map<String, Object>> documents, String schemaName) {
        logger.debug("Validating {} documents against schema: {}", documents.size(), schemaName);

        List<String> allErrors = new ArrayList<>();

        for (int i = 0; i < documents.size(); i++) {
            ValidationResult result = validate(documents.get(i), schemaName);
            if (!result.isValid()) {
                // Prefix errors with document index
                final int docIndex = i;  // Make effectively final for lambda
                result.getErrors().forEach(error ->
                        allErrors.add("Document[" + docIndex + "]: " + error)
                );
            }
        }

        if (allErrors.isEmpty()) {
            return ValidationResult.success();
        } else {
            return ValidationResult.failure(allErrors);
        }
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
