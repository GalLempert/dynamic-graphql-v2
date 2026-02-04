package sigma.model.filter;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents the result of filter translation for PostgreSQL queries.
 * Contains the WHERE clause, ORDER BY clause, parameters, and pagination settings.
 */
public class FilterResult {

    private final String whereClause;
    private final String orderByClause;
    private final Map<String, Object> parameters;
    private final Integer limit;
    private final Integer offset;

    private FilterResult(Builder builder) {
        this.whereClause = builder.whereClause;
        this.orderByClause = builder.orderByClause;
        this.parameters = builder.parameters;
        this.limit = builder.limit;
        this.offset = builder.offset;
    }

    public String getWhereClause() {
        return whereClause;
    }

    public String getOrderByClause() {
        return orderByClause;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public Integer getLimit() {
        return limit;
    }

    public Integer getOffset() {
        return offset;
    }

    public boolean hasWhereClause() {
        return whereClause != null && !whereClause.isEmpty() && !"1=1".equals(whereClause);
    }

    public boolean hasOrderByClause() {
        return orderByClause != null && !orderByClause.isEmpty();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String whereClause;
        private String orderByClause;
        private Map<String, Object> parameters = new HashMap<>();
        private Integer limit;
        private Integer offset;

        public Builder whereClause(String whereClause) {
            this.whereClause = whereClause;
            return this;
        }

        public Builder orderByClause(String orderByClause) {
            this.orderByClause = orderByClause;
            return this;
        }

        public Builder parameters(Map<String, Object> parameters) {
            this.parameters = new HashMap<>(parameters);
            return this;
        }

        public Builder addParameters(Map<String, Object> parameters) {
            this.parameters.putAll(parameters);
            return this;
        }

        public Builder limit(Integer limit) {
            this.limit = limit;
            return this;
        }

        public Builder offset(Integer offset) {
            this.offset = offset;
            return this;
        }

        public FilterResult build() {
            return new FilterResult(this);
        }
    }

    @Override
    public String toString() {
        return "FilterResult{" +
                "whereClause='" + whereClause + '\'' +
                ", orderByClause='" + orderByClause + '\'' +
                ", parameters=" + parameters +
                ", limit=" + limit +
                ", offset=" + offset +
                '}';
    }
}
