package dev.julioperez.nls.productsearchresponses.infrastructure.http;

import dev.julioperez.nls.productsearchresponses.domain.SearchVariantFact;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record ProductSearchVariantRequest(
        @NotNull UUID id,
        @NotBlank @Size(max = 80) String color,
        @NotBlank @Size(max = 40) String size,
        @NotNull @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal price,
        @Min(0) int stock) {

    SearchVariantFact toFact() {
        return new SearchVariantFact(id, color, size, price, stock);
    }
}
