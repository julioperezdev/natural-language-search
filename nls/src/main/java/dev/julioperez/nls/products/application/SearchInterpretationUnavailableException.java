package dev.julioperez.nls.products.application;

public class SearchInterpretationUnavailableException extends RuntimeException {
    public SearchInterpretationUnavailableException() {
        super("Natural-language interpretation is temporarily unavailable.");
    }
}
