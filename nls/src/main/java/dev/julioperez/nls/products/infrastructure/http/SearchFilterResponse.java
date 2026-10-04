package dev.julioperez.nls.products.infrastructure.http;

public record SearchFilterResponse(String field, String operator, Object value) {
}
