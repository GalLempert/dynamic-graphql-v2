package sigma.model.filter;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Represents a filter request from the client
 * Structure:
 * {
 *   "filter": { "$and": [...], "$or": [...], "fieldName": "value", ... },
 *   "options": { "sort": {...}, "limit": 50, "skip": 0, "projection": {...} }
 * }
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FilterRequest {

    @JsonProperty("filter")
    private Map<String, Object> filter;

    @JsonProperty("options")
    private FilterOptions options;

    /**
     * Filter options for sorting, pagination, and projection
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FilterOptions {

        @JsonProperty("sort")
        private Map<String, Integer> sort;

        @JsonProperty("limit")
        private Integer limit;

        @JsonProperty("skip")
        private Integer skip;

        @JsonProperty("projection")
        private Map<String, Integer> projection;
    }
}
