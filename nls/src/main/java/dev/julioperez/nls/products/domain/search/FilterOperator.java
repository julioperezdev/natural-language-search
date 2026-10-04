package dev.julioperez.nls.products.domain.search;

import java.util.Arrays;

public enum FilterOperator {
    EQUALS("="),
    NOT_EQUALS("!="),
    GREATER_THAN(">"),
    LESS_THAN("<"),
    LESS_THAN_OR_EQUAL("<="),
    GREATER_THAN_OR_EQUAL(">="),
    CONTAINS("CONTAINS"),
    NOT_CONTAINS("NOT_CONTAINS"),
    IN("IN"),
    NOT_IN("NOT_IN"),
    INCLUDES("INCLUDES"),
    INCLUDES_OR("INCLUDES_OR");

    private final String value;

    FilterOperator(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static FilterOperator fromValue(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Filter operator is required.");
        }

        String normalized = raw.trim().toUpperCase();
        return switch (normalized) {
            case "=", "EQ", "EQUALS" -> EQUALS;
            case "!=", "NE", "NOT_EQUALS" -> NOT_EQUALS;
            case ">", "GT", "GREATER_THAN" -> GREATER_THAN;
            case "<", "LT", "LESS_THAN" -> LESS_THAN;
            case "<=", "LTE", "LESS_THAN_OR_EQUAL" -> LESS_THAN_OR_EQUAL;
            case ">=", "GTE", "GREATER_THAN_OR_EQUAL" -> GREATER_THAN_OR_EQUAL;
            default -> Arrays.stream(values())
                    .filter(operator -> operator.value.equalsIgnoreCase(raw.trim())
                            || operator.name().equals(normalized))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown filter operator."));
        };
    }
}
