package dev.julioperez.nls.productsearch.infrastructure.http;

import dev.julioperez.nls.products.domain.search.ProductSearchPage;
import dev.julioperez.nls.productsearch.domain.ProductSearchAnswerOutcome;

public record ProductSearchAnswerResponse(
        ProductSearchAnswerOutcome outcome,
        String reply,
        ProductSearchPage results) {
}
