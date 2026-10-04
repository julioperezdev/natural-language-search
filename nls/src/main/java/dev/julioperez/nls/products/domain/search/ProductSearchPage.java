package dev.julioperez.nls.products.domain.search;

import java.util.List;

public record ProductSearchPage(List<ProductSearchItem> items, long total, int limit, int offset) {
    public ProductSearchPage {
        items = List.copyOf(items);
    }
}
