package dev.julioperez.nls.productsearchresponses.infrastructure.http;

import dev.julioperez.nls.productsearchresponses.domain.SearchProductFact;
import dev.julioperez.nls.productsearchresponses.domain.SearchVariantFact;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record ProductSearchResultItemRequest(
        @NotNull UUID id,
        @NotBlank @Size(max = 240) String name,
        @NotBlank @Size(max = 120) String category,
        @NotNull @Size(max = 500) List<@Valid ProductSearchVariantRequest> variants) {

    SearchProductFact toFact() {
        List<SearchVariantFact> facts = variants.stream()
                .map(ProductSearchVariantRequest::toFact)
                .toList();
        return new SearchProductFact(id, name, category, facts);
    }
}
