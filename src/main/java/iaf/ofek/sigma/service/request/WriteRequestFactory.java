package iaf.ofek.sigma.service.request;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import iaf.ofek.sigma.dto.request.*;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.Map;

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
                UpsertRequestBody upsertBody = objectMapper.readValue(body, UpsertRequestBody.class);
                return new UpsertRequest(upsertBody.filter, upsertBody.document, requestId);
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid JSON body for UPSERT: " + e.getMessage(), e);
            }
        }

        private static class UpsertRequestBody {
            public Map<String, Object> filter;
            public Map<String, Object> document;
        }
    }

    /**
     * PATCH → UpdateRequest
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
                UpdateRequestBody updateBody = objectMapper.readValue(body, UpdateRequestBody.class);
                boolean updateMultiple = updateBody.updateMultiple != null && updateBody.updateMultiple;
                return new UpdateRequest(updateBody.filter, updateBody.updates, requestId, updateMultiple);
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid JSON body for UPDATE: " + e.getMessage(), e);
            }
        }

        private static class UpdateRequestBody {
            public Map<String, Object> filter;
            public Map<String, Object> updates;
            public Boolean updateMultiple;
        }
    }

    /**
     * DELETE → DeleteRequest
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
                    DeleteRequestBody deleteBody = objectMapper.readValue(body, DeleteRequestBody.class);
                    filter = deleteBody.filter;
                    deleteMultiple = deleteBody.deleteMultiple != null && deleteBody.deleteMultiple;
                } else {
                    String filterParam = request.getParameter("filter");
                    if (filterParam == null) {
                        throw new IllegalArgumentException("DELETE requires filter in body or query parameter");
                    }
                    filter = objectMapper.readValue(filterParam, new TypeReference<Map<String, Object>>() {});
                }

                return new DeleteRequest(filter, requestId, deleteMultiple);
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid DELETE request: " + e.getMessage(), e);
            }
        }

        private static class DeleteRequestBody {
            public Map<String, Object> filter;
            public Boolean deleteMultiple;
        }
    }
}
