package dev.julioperez.nls.products.application;

public class SearchInterpretationFailedException extends RuntimeException {
    public SearchInterpretationFailedException() {
        super("Search intent could not be interpreted safely.");
    }

}
