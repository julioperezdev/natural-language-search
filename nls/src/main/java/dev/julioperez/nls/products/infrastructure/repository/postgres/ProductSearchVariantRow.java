package dev.julioperez.nls.products.infrastructure.repository.postgres;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductSearchVariantRow(
        UUID productId,
        UUID variantId,
        String color,
        String size,
        BigDecimal price,
        Integer stock) {
}
