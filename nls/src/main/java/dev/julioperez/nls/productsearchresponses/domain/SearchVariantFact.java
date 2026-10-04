package dev.julioperez.nls.productsearchresponses.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record SearchVariantFact(UUID id, String color, String size, BigDecimal price, int stock) {
}
