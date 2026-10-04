package dev.julioperez.nls.productsearch.application;

import dev.julioperez.nls.products.domain.search.ProductSearchPage;
import dev.julioperez.nls.productsearch.domain.ProductSearchAnswerOutcome;

public record ProductSearchAnswer(ProductSearchAnswerOutcome outcome, String reply, ProductSearchPage results) {
}
