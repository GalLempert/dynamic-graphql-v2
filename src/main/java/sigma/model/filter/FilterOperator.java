package sigma.model.filter;

import sigma.model.filter.operator.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Enum that maps operator symbols to their strategy implementations
 * Uses Strategy pattern for clean operator behavior
 */
public enum FilterOperator {
    // Comparison operators
    EQ(new EqualOperator()),
    NE(new NotEqualOperator()),
    GT(new GreaterThanOperator()),
    GTE(new GreaterThanEqualOperator()),
    LT(new LessThanOperator()),
    LTE(new LessThanEqualOperator()),
    IN(new InOperator()),
    NIN(new NotInOperator()),

    // Logical operators
    AND(new AndOperator()),
    OR(new OrOperator()),
    NOT(new NotOperator()),
    NOR(new NorOperator()),

    // String operators
    REGEX(new RegexOperator()),

    // Existence operators
    EXISTS(new ExistsOperator()),
    TYPE(new TypeOperator());

    private final OperatorStrategy strategy;
    private static final Map<String, FilterOperator> OPERATOR_MAP = new HashMap<>();

    static {
        for (FilterOperator op : values()) {
            OPERATOR_MAP.put(op.getMongoOperator(), op);
            OPERATOR_MAP.put(op.name().toLowerCase(), op);
        }
    }

    FilterOperator(OperatorStrategy strategy) {
        this.strategy = strategy;
    }

    public OperatorStrategy getStrategy() {
        return strategy;
    }

    public String getMongoOperator() {
        return strategy.getMongoOperator();
    }

    public boolean isLogical() {
        return strategy.isLogical();
    }

    /**
     * Looks up an operator by its MongoDB symbol or enum name
     */
    public static FilterOperator fromString(String operator) {
        if (operator == null) {
            return EQ; // Default to equality
        }

        FilterOperator op = OPERATOR_MAP.get(operator.toLowerCase());
        if (op != null) {
            return op;
        }

        // Try exact match with MongoDB operator
        op = OPERATOR_MAP.get(operator);
        if (op != null) {
            return op;
        }

        throw new IllegalArgumentException("Unknown filter operator: " + operator);
    }
}
