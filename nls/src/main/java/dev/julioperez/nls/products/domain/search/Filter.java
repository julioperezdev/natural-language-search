package dev.julioperez.nls.products.domain.search;

public record Filter(String field, FilterOperator operator, Object value) {
}
