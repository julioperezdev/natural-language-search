package dev.julioperez.nls.products.application;

public class SearchInterpretationFailedException extends RuntimeException {
    private final Reason reason;
    private final String field;

    public SearchInterpretationFailedException() {
        this(Reason.INVALID_PROVIDER_ANSWER, null);
    }

    public SearchInterpretationFailedException(Reason reason, String field) {
        super("Search intent could not be interpreted safely.");
        this.reason = reason == null ? Reason.INVALID_PROVIDER_ANSWER : reason;
        this.field = field;
    }

    public Reason reason() {
        return reason;
    }

    public String field() {
        return field;
    }

    public enum Reason {
        INVALID_INPUT,
        EMPTY_PROVIDER_RESPONSE,
        INVALID_PROVIDER_ANSWER,
        LOW_CONFIDENCE,
        NOT_A_SEARCH,
        NO_SEARCH_FILTERS,
        UNEXPECTED_PROVIDER_RESPONSE
    }
}
