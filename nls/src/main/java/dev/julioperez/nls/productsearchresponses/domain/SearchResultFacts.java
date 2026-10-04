package dev.julioperez.nls.productsearchresponses.domain;

import java.util.List;

public record SearchResultFacts(List<SearchProductFact> items, long total, int limit, int offset) {
    public SearchResultFacts {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
