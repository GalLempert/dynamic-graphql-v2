package iaf.ofek.sigma.model.filter;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Represents a filter request from the client
 * Structure:
 * {
 *   "filter": { "$and": [...], "$or": [...], "fieldName": "value", ... },
 *   "options": { "sort": {...}, "limit": 50, "skip": 0, "projection": {...} }
 * }
 */
public class FilterRequest {

    @JsonProperty("filter")
    private Map<String, Object> filter;

    @JsonProperty("options")
    private FilterOptions options;

    public FilterRequest() {
    }

    public FilterRequest(Map<String, Object> filter, FilterOptions options) {
        this.filter = filter;
        this.options = options;
    }

    public Map<String, Object> getFilter() {
        return filter;
    }

    public void setFilter(Map<String, Object> filter) {
        this.filter = filter;
    }

    public FilterOptions getOptions() {
        return options;
    }

    public void setOptions(FilterOptions options) {
        this.options = options;
    }

    @Override
    public String toString() {
        return "FilterRequest{" +
                "filter=" + filter +
                ", options=" + options +
                '}';
    }

    /**
     * Filter options for sorting, pagination, and projection
     */
    public static class FilterOptions {

        @JsonProperty("sort")
        private Map<String, Integer> sort;

        @JsonProperty("limit")
        private Integer limit;

        @JsonProperty("skip")
        private Integer skip;

        @JsonProperty("projection")
        private Map<String, Integer> projection;

        public FilterOptions() {
        }

        public FilterOptions(Map<String, Integer> sort, Integer limit, Integer skip, Map<String, Integer> projection) {
            this.sort = sort;
            this.limit = limit;
            this.skip = skip;
            this.projection = projection;
        }

        public Map<String, Integer> getSort() {
            return sort;
        }

        public void setSort(Map<String, Integer> sort) {
            this.sort = sort;
        }

        public Integer getLimit() {
            return limit;
        }

        public void setLimit(Integer limit) {
            this.limit = limit;
        }

        public Integer getSkip() {
            return skip;
        }

        public void setSkip(Integer skip) {
            this.skip = skip;
        }

        public Map<String, Integer> getProjection() {
            return projection;
        }

        public void setProjection(Map<String, Integer> projection) {
            this.projection = projection;
        }

        @Override
        public String toString() {
            return "FilterOptions{" +
                    "sort=" + sort +
                    ", limit=" + limit +
                    ", skip=" + skip +
                    ", projection=" + projection +
                    '}';
        }
    }
}
