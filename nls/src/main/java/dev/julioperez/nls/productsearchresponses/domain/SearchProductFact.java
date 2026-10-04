package dev.julioperez.nls.productsearchresponses.domain;

import java.util.List;
import java.util.UUID;

public record SearchProductFact(
        UUID id,
        String name,
        String category,
        List<SearchVariantFact> variants) {
    public SearchProductFact {
        variants = variants == null ? List.of() : List.copyOf(variants);
    }
}
