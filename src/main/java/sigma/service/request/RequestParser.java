package sigma.service.request;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import sigma.dto.request.*;
import sigma.model.Endpoint;
import sigma.model.filter.FilterRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses HTTP requests into strongly-typed QueryRequest and WriteRequest objects
 * Single Responsibility: Request parsing and deserialization
 */
@Service
public class RequestParser {

    private static final Logger logger = LoggerFactory.getLogger(RequestParser.class);
    private final ObjectMapper objectMapper;
    private final WriteRequestFactory writeRequestFactory;

    public RequestParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.writeRequestFactory = new WriteRequestFactory(objectMapper);
    }

    /**
     * Parses an HTTP request into a QueryRequest object
     *
     * @param method HTTP method (GET, POST, etc.)
     * @param body Request body (may be null)
     * @param request HttpServletRequest for accessing parameters
     * @param endpoint Endpoint configuration
     * @return Strongly-typed QueryRequest
     */
    public QueryRequest parse(String method, String body, HttpServletRequest request, Endpoint endpoint) {
        logger.debug("Parsing request: method={}, hasBody={}", method, body != null && !body.isEmpty());

        // Check for sequence-based query
        String sequenceParam = request.getParameter("sequence");
        if (sequenceParam != null) {
            return parseSequenceRequest(sequenceParam, request.getParameter("bulkSize"), endpoint);
        }

        // Check for POST with filter body
        if ("POST".equalsIgnoreCase(method) && body != null && !body.isEmpty()) {
            return parseFilteredPostRequest(body);
        }

        // Check for GET with filter parameters
        if ("GET".equalsIgnoreCase(method) && hasFilterParameters(request)) {
            return parseFilteredGetRequest(request);
        }

        // Default: full collection query
        return new FullCollectionRequest();
    }

    /**
     * Parses a sequence-based request
     */
    private SequenceQueryRequest parseSequenceRequest(String sequenceParam, String bulkSizeParam, Endpoint endpoint) {
        try {
            long sequence = Long.parseLong(sequenceParam);
            int bulkSize = bulkSizeParam != null ?
                    Integer.parseInt(bulkSizeParam) :
                    endpoint.getDefaultBulkSize();

            logger.debug("Parsed sequence request: sequence={}, bulkSize={}", sequence, bulkSize);
            return new SequenceQueryRequest(sequence, bulkSize);

        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid sequence or bulkSize parameter: " + e.getMessage(), e);
        }
    }

    /**
     * Parses a POST request with filter body
     */
    private FilteredQueryRequest parseFilteredPostRequest(String body) {
        try {
            FilterRequest filterRequest = objectMapper.readValue(body, FilterRequest.class);
            logger.debug("Parsed filtered POST request: {}", filterRequest);
            return new FilteredQueryRequest(filterRequest);

        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JSON filter body: " + e.getMessage(), e);
        }
    }

    /**
     * Parses a GET request with filter parameters
     */
    private FilteredQueryRequest parseFilteredGetRequest(HttpServletRequest request) {
        Map<String, String> params = extractQueryParameters(request);

        // Extract filter fields (non-special parameters)
        Map<String, Object> filterMap = new HashMap<>();
        params.forEach((key, value) -> {
            if (!isSpecialParameter(key)) {
                filterMap.put(key, value);
            }
        });

        // Extract options
        FilterRequest.FilterOptions options = extractOptionsFromParams(params);

        // Create FilterRequest
        FilterRequest filterRequest = new FilterRequest(filterMap, options);
        logger.debug("Parsed filtered GET request: {}", filterRequest);

        return new FilteredQueryRequest(filterRequest);
    }

    /**
     * Extracts all query parameters from the request
     */
    private Map<String, String> extractQueryParameters(HttpServletRequest request) {
        Map<String, String> params = new HashMap<>();
        request.getParameterMap().forEach((key, values) -> {
            if (values.length > 0) {
                params.put(key, values[0]);
            }
        });
        return params;
    }

    /**
     * Extracts filter options (sort, limit, skip) from query parameters
     */
    private FilterRequest.FilterOptions extractOptionsFromParams(Map<String, String> params) {
        FilterRequest.FilterOptions options = new FilterRequest.FilterOptions();

        // Parse limit
        if (params.containsKey("limit")) {
            try {
                options.setLimit(Integer.parseInt(params.get("limit")));
            } catch (NumberFormatException e) {
                logger.warn("Invalid limit parameter: {}", params.get("limit"));
            }
        }

        // Parse skip
        if (params.containsKey("skip")) {
            try {
                options.setSkip(Integer.parseInt(params.get("skip")));
            } catch (NumberFormatException e) {
                logger.warn("Invalid skip parameter: {}", params.get("skip"));
            }
        }

        // Parse sort
        if (params.containsKey("sort")) {
            String sortField = params.get("sort");
            int direction = 1; // ascending by default

            if (sortField.startsWith("-")) {
                direction = -1;
                sortField = sortField.substring(1);
            }

            options.setSort(Map.of(sortField, direction));
        }

        return options;
    }

    /**
     * Checks if request has filter parameters (excluding special parameters)
     */
    private boolean hasFilterParameters(HttpServletRequest request) {
        return request.getParameterMap().keySet().stream()
                .anyMatch(param -> !isSpecialParameter(param));
    }

    /**
     * Checks if a parameter is a special parameter (not a filter field)
     */
    private boolean isSpecialParameter(String param) {
        return param.equals("sequence") || param.equals("bulkSize") ||
               param.equals("limit") || param.equals("skip") || param.equals("sort");
    }

    // ========== WRITE REQUEST PARSING ==========

    /**
     * Parses an HTTP request into a WriteRequest object
     *
     * @param method HTTP method (POST, PUT, PATCH, DELETE)
     * @param body Request body (may be null for DELETE)
     * @param request HttpServletRequest for accessing parameters
     * @param endpoint Endpoint configuration
     * @return Strongly-typed WriteRequest
     */
    public WriteRequest parseWrite(String method, String body, HttpServletRequest request, Endpoint endpoint) {
        logger.debug("Parsing write request: method={}, hasBody={}", method, body != null && !body.isEmpty());

        // Extract request ID from header (for audit trail)
        String requestId = request.getHeader("X-Request-ID");
        if (requestId == null || requestId.isEmpty()) {
            requestId = "req-" + System.currentTimeMillis();
        }

        // Uses Strategy pattern via factory - ZERO switch statements!
        return writeRequestFactory.create(method, body, request, requestId);
    }

    /**
     * Parses POST request → CreateRequest
     */
    private WriteRequest parseCreateRequest(String body, String requestId) {
        if (body == null || body.isEmpty()) {
            throw new IllegalArgumentException("POST request requires a body");
        }

        try {
            // Try parsing as array first (bulk insert)
            if (body.trim().startsWith("[")) {
                List<Map<String, Object>> documents = objectMapper.readValue(
                        body,
                        new TypeReference<List<Map<String, Object>>>() {}
                );
                logger.debug("Parsed bulk CREATE request: {} documents", documents.size());
                return new CreateRequest(documents, requestId);
            } else {
                // Single document
                Map<String, Object> document = objectMapper.readValue(
                        body,
                        new TypeReference<Map<String, Object>>() {}
                );
                logger.debug("Parsed single CREATE request");
                return new CreateRequest(document, requestId);
            }

        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JSON body for CREATE: " + e.getMessage(), e);
        }
    }

    /**
     * Parses PUT request → UpsertRequest
     */
    private WriteRequest parseUpsertRequest(String body, HttpServletRequest request, String requestId) {
        if (body == null || body.isEmpty()) {
            throw new IllegalArgumentException("PUT request requires a body");
        }

        try {
            Map<String, Object> jsonBody = objectMapper.readValue(
                    body,
                    new TypeReference<Map<String, Object>>() {}
            );

            // Check if body contains "filter" and "document" fields (complex format)
            if (jsonBody.containsKey("filter") && jsonBody.containsKey("document")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> filter = (Map<String, Object>) jsonBody.get("filter");
                @SuppressWarnings("unchecked")
                Map<String, Object> document = (Map<String, Object>) jsonBody.get("document");

                logger.debug("Parsed UPSERT request with explicit filter");
                return new UpsertRequest(filter, document, requestId);

            } else {
                // Simple format: filter from query params, document from body
                Map<String, Object> filter = extractFilterFromParams(request);
                logger.debug("Parsed UPSERT request with query param filter");
                return new UpsertRequest(filter, jsonBody, requestId);
            }

        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JSON body for UPSERT: " + e.getMessage(), e);
        }
    }

    /**
     * Parses PATCH request → UpdateRequest
     */
    private WriteRequest parseUpdateRequest(String body, HttpServletRequest request, String requestId) {
        if (body == null || body.isEmpty()) {
            throw new IllegalArgumentException("PATCH request requires a body");
        }

        try {
            Map<String, Object> jsonBody = objectMapper.readValue(
                    body,
                    new TypeReference<Map<String, Object>>() {}
            );

            Map<String, Object> filter;
            Map<String, Object> updates;
            boolean updateMultiple = false;

            // Check if body contains "filter" and "updates" fields (complex format)
            if (jsonBody.containsKey("filter") && jsonBody.containsKey("updates")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> filterFromBody = (Map<String, Object>) jsonBody.get("filter");
                filter = filterFromBody;

                @SuppressWarnings("unchecked")
                Map<String, Object> updatesFromBody = (Map<String, Object>) jsonBody.get("updates");
                updates = updatesFromBody;

                // Check for updateMultiple flag
                if (jsonBody.containsKey("updateMultiple")) {
                    updateMultiple = (Boolean) jsonBody.get("updateMultiple");
                }

                logger.debug("Parsed UPDATE request with explicit filter");

            } else {
                // Simple format: filter from query params, updates from body
                filter = extractFilterFromParams(request);
                updates = jsonBody;

                // If no filter provided, treat as update multiple
                updateMultiple = filter.isEmpty();

                logger.debug("Parsed UPDATE request with query param filter");
            }

            return new UpdateRequest(filter, updates, requestId, updateMultiple);

        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JSON body for UPDATE: " + e.getMessage(), e);
        }
    }

    /**
     * Parses DELETE request → DeleteRequest
     */
    private WriteRequest parseDeleteRequest(String body, HttpServletRequest request, String requestId) {
        Map<String, Object> filter;
        boolean deleteMultiple = false;

        // Check if body is provided with explicit filter
        if (body != null && !body.isEmpty()) {
            try {
                Map<String, Object> jsonBody = objectMapper.readValue(
                        body,
                        new TypeReference<Map<String, Object>>() {}
                );

                if (jsonBody.containsKey("filter")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> filterFromBody = (Map<String, Object>) jsonBody.get("filter");
                    filter = filterFromBody;

                    // Check for deleteMultiple flag
                    if (jsonBody.containsKey("deleteMultiple")) {
                        deleteMultiple = (Boolean) jsonBody.get("deleteMultiple");
                    }

                    logger.debug("Parsed DELETE request with body filter");
                } else {
                    // Body is the filter itself
                    filter = jsonBody;
                    logger.debug("Parsed DELETE request with body as filter");
                }

            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid JSON body for DELETE: " + e.getMessage(), e);
            }

        } else {
            // No body, use query parameters as filter
            filter = extractFilterFromParams(request);

            // If no filter provided, this is dangerous - require explicit confirmation
            if (filter.isEmpty()) {
                throw new IllegalArgumentException("DELETE request requires a filter (query params or body)");
            }

            logger.debug("Parsed DELETE request with query param filter");
        }

        return new DeleteRequest(filter, requestId, deleteMultiple);
    }

    /**
     * Extracts filter from query parameters
     */
    private Map<String, Object> extractFilterFromParams(HttpServletRequest request) {
        Map<String, Object> filter = new HashMap<>();

        request.getParameterMap().forEach((key, values) -> {
            if (!isSpecialParameter(key) && values.length > 0) {
                // Simple equals filter for query params
                filter.put(key, Map.of("$eq", values[0]));
            }
        });

        return filter;
    }
}
