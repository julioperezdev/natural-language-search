package dev.julioperez.nls.products.domain.search;

public class ProductSearchException extends RuntimeException {
    private final String code;

    public ProductSearchException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
