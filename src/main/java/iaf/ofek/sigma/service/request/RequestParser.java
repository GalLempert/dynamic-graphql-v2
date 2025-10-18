package iaf.ofek.sigma.service.request;

import com.fasterxml.jackson.databind.ObjectMapper;
import iaf.ofek.sigma.dto.request.*;
import iaf.ofek.sigma.model.Endpoint;
import iaf.ofek.sigma.model.filter.FilterRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Parses HTTP requests into strongly-typed QueryRequest objects
 * Single Responsibility: Request parsing and deserialization
 */
@Service
public class RequestParser {

    private static final Logger logger = LoggerFactory.getLogger(RequestParser.class);
    private final ObjectMapper objectMapper;

    public RequestParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
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
}
