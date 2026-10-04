package dev.julioperez.nls.productsearchresponses.infrastructure.http;

import dev.julioperez.nls.productsearchresponses.domain.SearchResponseOutcome;

public record ProductSearchHumanizationResponse(SearchResponseOutcome outcome, String reply) {
}
