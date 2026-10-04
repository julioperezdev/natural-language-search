package dev.julioperez.nls.products.domain.search;

import java.util.List;
import java.util.UUID;

public record ProductSearchItem(
        UUID id,
        String name,
        String category,
        List<ProductVariantSummary> variants) {
    public ProductSearchItem {
        variants = variants == null ? List.of() : List.copyOf(variants);
    }
}
