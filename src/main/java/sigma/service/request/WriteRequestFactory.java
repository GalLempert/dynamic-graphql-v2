package sigma.service.request;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import sigma.dto.request.*;
import jakarta.servlet.http.HttpServletRequest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Factory for creating WriteRequest objects from HTTP requests
 * Uses Strategy pattern to eliminate switch statements
 * 
 * Each HTTP method has its own parsing strategy registered in a map
 */
public class WriteRequestFactory {

    private final Map<String, WriteRequestParser> parsers;

    public WriteRequestFactory(ObjectMapper objectMapper) {
        // Strategy pattern: Map of parsers instead of switch statement!
        this.parsers = Map.of(
            "POST", new PostRequestParser(objectMapper),
            "PUT", new PutRequestParser(objectMapper),
            "PATCH", new PatchRequestParser(objectMapper),
            "DELETE", new DeleteRequestParser(objectMapper)
        );
    }

    /**
     * Creates WriteRequest based on HTTP method
     * Uses Strategy pattern with map - ZERO switch statements!
     */
    public WriteRequest create(String method, String body, HttpServletRequest request, String requestId) {
        WriteRequestParser parser = parsers.get(method.toUpperCase());
        if (parser == null) {
            throw new IllegalArgumentException("Unsupported write method: " + method);
        }
        return parser.parse(body, request, requestId);
    }

    private static final Set<String> SPECIAL_PARAMS = Set.of("sequence", "bulkSize", "limit", "skip", "sort");

    /**
     * Extracts filter from query parameters, wrapping each value in an $eq operator.
     * Special pagination/sorting params are excluded.
     */
    private static Map<String, Object> extractFilterFromParams(HttpServletRequest request) {
        Map<String, Object> filter = new HashMap<>();
        request.getParameterMap().forEach((key, values) -> {
            if (!SPECIAL_PARAMS.contains(key) && values.length > 0) {
                filter.put(key, Map.of("$eq", values[0]));
            }
        });
        return filter;
    }

    /**
     * Strategy interface for parsing write requests
     */
    private interface WriteRequestParser {
        WriteRequest parse(String body, HttpServletRequest request, String requestId);
    }

    /**
     * POST → CreateRequest
     */
    private static class PostRequestParser implements WriteRequestParser {
        private final ObjectMapper objectMapper;

        PostRequestParser(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        @Override
        public WriteRequest parse(String body, HttpServletRequest request, String requestId) {
            if (body == null || body.isEmpty()) {
                throw new IllegalArgumentException("POST request requires a body");
            }

            try {
                if (body.trim().startsWith("[")) {
                    List<Map<String, Object>> documents = objectMapper.readValue(
                            body,
                            new TypeReference<List<Map<String, Object>>>() {}
                    );
                    return new CreateRequest(documents, requestId);
                } else {
                    Map<String, Object> document = objectMapper.readValue(
                            body,
                            new TypeReference<Map<String, Object>>() {}
                    );
                    return new CreateRequest(document, requestId);
                }
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid JSON body for CREATE: " + e.getMessage(), e);
            }
        }
    }

    /**
     * PUT → UpsertRequest
     * Supports two formats:
     * - Structured: {"filter": {...}, "document": {...}}
     * - Simple: filter from query params, body is the document
     */
    private static class PutRequestParser implements WriteRequestParser {
        private final ObjectMapper objectMapper;

        PutRequestParser(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        @Override
        public WriteRequest parse(String body, HttpServletRequest request, String requestId) {
            if (body == null || body.isEmpty()) {
                throw new IllegalArgumentException("PUT request requires a body");
            }

            try {
                Map<String, Object> jsonBody = objectMapper.readValue(
                        body, new TypeReference<Map<String, Object>>() {});

                if (jsonBody.containsKey("filter") && jsonBody.containsKey("document")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> filter = (Map<String, Object>) jsonBody.get("filter");
                    @SuppressWarnings("unchecked")
                    Map<String, Object> document = (Map<String, Object>) jsonBody.get("document");
                    return new UpsertRequest(filter, document, requestId);
                }

                // Simple format: filter from query params, body is the document
                Map<String, Object> filter = extractFilterFromParams(request);
                return new UpsertRequest(filter, jsonBody, requestId);
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid JSON body for UPSERT: " + e.getMessage(), e);
            }
        }
    }

    /**
     * PATCH → UpdateRequest
     * Supports two formats:
     * - Structured: {"filter": {...}, "updates": {...}, "updateMultiple": bool}
     * - Simple: filter from query params, body is the updates
     */
    private static class PatchRequestParser implements WriteRequestParser {
        private final ObjectMapper objectMapper;

        PatchRequestParser(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        @Override
        public WriteRequest parse(String body, HttpServletRequest request, String requestId) {
            if (body == null || body.isEmpty()) {
                throw new IllegalArgumentException("PATCH request requires a body");
            }

            try {
                Map<String, Object> jsonBody = objectMapper.readValue(
                        body, new TypeReference<Map<String, Object>>() {});

                if (jsonBody.containsKey("filter") && jsonBody.containsKey("updates")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> filter = (Map<String, Object>) jsonBody.get("filter");
                    @SuppressWarnings("unchecked")
                    Map<String, Object> updates = (Map<String, Object>) jsonBody.get("updates");
                    boolean updateMultiple = jsonBody.containsKey("updateMultiple")
                            && Boolean.TRUE.equals(jsonBody.get("updateMultiple"));
                    return new UpdateRequest(filter, updates, requestId, updateMultiple);
                }

                // Simple format: filter from query params, body is the updates
                Map<String, Object> filter = extractFilterFromParams(request);
                boolean updateMultiple = filter.isEmpty();
                return new UpdateRequest(filter, jsonBody, requestId, updateMultiple);
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid JSON body for UPDATE: " + e.getMessage(), e);
            }
        }
    }

    /**
     * DELETE → DeleteRequest
     * Supports:
     * - Body with {"filter": {...}, "deleteMultiple": bool}
     * - No body: filter extracted from query parameters (e.g. ?role=guest)
     */
    private static class DeleteRequestParser implements WriteRequestParser {
        private final ObjectMapper objectMapper;

        DeleteRequestParser(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        @Override
        public WriteRequest parse(String body, HttpServletRequest request, String requestId) {
            try {
                Map<String, Object> filter;
                boolean deleteMultiple = false;

                if (body != null && !body.isEmpty()) {
                    Map<String, Object> jsonBody = objectMapper.readValue(
                            body, new TypeReference<Map<String, Object>>() {});

                    if (jsonBody.containsKey("filter")) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> filterFromBody = (Map<String, Object>) jsonBody.get("filter");
                        filter = filterFromBody;
                        deleteMultiple = jsonBody.containsKey("deleteMultiple")
                                && Boolean.TRUE.equals(jsonBody.get("deleteMultiple"));
                    } else {
                        // Body itself is the filter
                        filter = jsonBody;
                    }
                } else {
                    // No body: extract filter from query parameters
                    filter = extractFilterFromParams(request);
                    if (filter.isEmpty()) {
                        throw new IllegalArgumentException("DELETE request requires a filter (query params or body)");
                    }
                }

                return new DeleteRequest(filter, requestId, deleteMultiple);
            } catch (IllegalArgumentException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid DELETE request: " + e.getMessage(), e);
            }
        }
    }
}
