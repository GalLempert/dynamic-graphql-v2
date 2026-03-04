package sigma.service.enums;

import sigma.dto.response.*;
import sigma.model.Endpoint;
import sigma.model.enums.DynamicEnum;
import sigma.model.enums.EnumFieldBinding;
import sigma.model.enums.EnumFieldPointer;
import sigma.model.enums.EnumValue;
import sigma.model.schema.JsonSchema;
import sigma.model.schema.SchemaReference;
import sigma.service.schema.SchemaManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Transforms query responses by enriching enum-backed fields with both code and literal values.
 */
@Service
public class EnumResponseTransformer {

    private static final Logger logger = LoggerFactory.getLogger(EnumResponseTransformer.class);

    private final SchemaManager schemaManager;
    private final EnumRegistry enumRegistry;

    public EnumResponseTransformer(SchemaManager schemaManager, EnumRegistry enumRegistry) {
        this.schemaManager = schemaManager;
        this.enumRegistry = enumRegistry;
    }

    public QueryResponse transform(QueryResponse response, Endpoint endpoint) {
        if (endpoint == null) {
            return response;
        }

        if (!enumRegistry.isEnabled()) {
            return response;
        }

        SchemaReference schemaReference = endpoint.getSchemaReference();
        if (schemaReference == null) {
            return response;
        }

        JsonSchema schema = schemaManager.getSchema(schemaReference.getSchemaName());
        if (schema == null) {
            return response;
        }

        List<EnumFieldBinding> bindings = schema.getEnumBindings();
        if (bindings.isEmpty()) {
            return response;
        }

        ResponseVisitor<Response> visitor = new Visitor(bindings);
        return (QueryResponse) response.accept(visitor);
    }

    private class Visitor implements ResponseVisitor<Response> {

        private final List<EnumFieldBinding> bindings;

        private Visitor(List<EnumFieldBinding> bindings) {
            this.bindings = bindings;
        }

        @Override
        public Response visitDocumentList(DocumentListResponse response) {
            transformDocuments(response.getDocuments());
            return response;
        }

        @Override
        public Response visitSequence(SequenceResponse response) {
            transformDocuments(response.getData());
            return response;
        }

        @Override
        public Response visitError(ErrorResponse response) {
            return response;
        }

        @Override
        public Response visitCreate(CreateResponse response) {
            return response;
        }

        @Override
        public Response visitUpdate(UpdateResponse response) {
            return response;
        }

        @Override
        public Response visitDelete(DeleteResponse response) {
            return response;
        }

        @Override
        public Response visitUpsert(UpsertResponse response) {
            return response;
        }

        private void transformDocuments(List<? extends Map<String, Object>> documents) {
            if (documents == null || documents.isEmpty()) {
                return;
            }
            documents.forEach(this::transformDocument);
        }

        private void transformDocument(Map<String, Object> document) {
            if (document == null || document.isEmpty()) {
                return;
            }

            bindings.forEach(binding -> {
                Optional<DynamicEnum> enumOpt = enumRegistry.getEnum(binding.getEnumName());
                if (enumOpt.isEmpty()) {
                    logger.warn("Enum '{}' referenced in schema missing from registry", binding.getEnumName());
                    return;
                }
                DynamicEnum dynamicEnum = enumOpt.get();
                transformValue(document, binding.getPointer().getSegments(), 0, dynamicEnum);
            });
        }

        private Object transformValue(Object current, List<EnumFieldPointer.Segment> segments, int index, DynamicEnum dynamicEnum) {
            if (index == segments.size()) {
                return enrichValue(current, dynamicEnum);
            }
            EnumFieldPointer.Segment segment = segments.get(index);
            return segment.traverse(current, next -> transformValue(next, segments, index + 1, dynamicEnum));
        }

        private Object enrichValue(Object value, DynamicEnum dynamicEnum) {
            if (value == null) {
                return null;
            }

            if (value instanceof List<?> list) {
                return list.stream()
                        .map(element -> enrichValue(element, dynamicEnum))
                        .collect(Collectors.toList());
            }

            if (value instanceof Map<?, ?> map) {
                return value;
            }

            String code = value instanceof String ? (String) value : String.valueOf(value);
            String literal = dynamicEnum.getValue(code)
                    .map(EnumValue::getLiteral)
                    .orElse(code);

            Map<String, Object> enriched = new LinkedHashMap<>();
            enriched.put("code", value);
            enriched.put("value", literal);
            return enriched;
        }
    }
}
