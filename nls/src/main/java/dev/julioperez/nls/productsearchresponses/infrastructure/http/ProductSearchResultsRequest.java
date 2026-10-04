package dev.julioperez.nls.productsearchresponses.infrastructure.http;

import dev.julioperez.nls.productsearchresponses.domain.SearchProductFact;
import dev.julioperez.nls.productsearchresponses.domain.SearchResultFacts;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ProductSearchResultsRequest(
        @NotNull @Size(max = 50) List<@Valid ProductSearchResultItemRequest> items,
        @NotNull @PositiveOrZero Long total,
        @NotNull @Min(1) @Max(50) Integer limit,
        @NotNull @Min(0) @Max(10000) Integer offset) {

    public SearchResultFacts toFacts() {
        List<SearchProductFact> facts = items.stream()
                .map(ProductSearchResultItemRequest::toFact)
                .toList();
        return new SearchResultFacts(facts, total, limit, offset);
    }
}
