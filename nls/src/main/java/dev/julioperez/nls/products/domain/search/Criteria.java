package dev.julioperez.nls.products.domain.search;

import java.util.List;

public record Criteria(List<Filter> filters, Order order, Integer limit, Integer offset) {
    public Criteria {
        filters = filters == null ? List.of() : List.copyOf(filters);
        limit = limit == null ? 10 : limit;
        offset = offset == null ? 0 : offset;
    }
}
