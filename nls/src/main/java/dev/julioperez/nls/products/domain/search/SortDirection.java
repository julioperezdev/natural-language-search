package dev.julioperez.nls.products.domain.search;

public enum SortDirection {
    ASC,
    DESC;

    public static SortDirection fromValue(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Sort direction is required.");
        }

        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Sort direction must be ASC or DESC.", exception);
        }
    }
}
