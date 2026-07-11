package sigma.console.model;

import sigma.model.filter.FilterOperator;

import java.util.Arrays;
import java.util.Optional;

/**
 * The product-language vocabulary for search conditions.
 *
 * The console never shows engine symbols (eq, gte, regex...) to people;
 * it speaks in terms like "equals", "at least", "matches pattern".
 * This enum is the single translation table between the two worlds.
 */
public enum FriendlyOperator {

    EQUALS("equals", FilterOperator.EQ),
    NOT_EQUALS("is not", FilterOperator.NE),
    ONE_OF("one of", FilterOperator.IN),
    NOT_ONE_OF("none of", FilterOperator.NIN),
    GREATER_THAN("greater than", FilterOperator.GT),
    AT_LEAST("at least", FilterOperator.GTE),
    LESS_THAN("less than", FilterOperator.LT),
    AT_MOST("at most", FilterOperator.LTE),
    MATCHES("matches pattern", FilterOperator.REGEX),
    HAS_VALUE("has a value", FilterOperator.EXISTS);

    private final String label;
    private final FilterOperator engineOperator;

    FriendlyOperator(String label, FilterOperator engineOperator) {
        this.label = label;
        this.engineOperator = engineOperator;
    }

    /** The label people see and the value stored in resource YAML files. */
    public String getLabel() {
        return label;
    }

    /** The engine operator this compiles to. */
    public FilterOperator getEngineOperator() {
        return engineOperator;
    }

    public static Optional<FriendlyOperator> fromLabel(String label) {
        if (label == null) {
            return Optional.empty();
        }
        String normalized = label.trim().toLowerCase();
        return Arrays.stream(values())
                .filter(op -> op.label.equals(normalized))
                .findFirst();
    }

    public static Optional<FriendlyOperator> fromEngineOperator(FilterOperator engineOperator) {
        return Arrays.stream(values())
                .filter(op -> op.engineOperator == engineOperator)
                .findFirst();
    }
}
