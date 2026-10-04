package dev.julioperez.nls.products.domain.search;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductVariantSummary(
        UUID id,
        String color,
        String size,
        BigDecimal price,
        int stock) {
}
