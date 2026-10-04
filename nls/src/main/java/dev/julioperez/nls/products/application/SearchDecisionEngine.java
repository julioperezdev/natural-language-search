package dev.julioperez.nls.products.application;

import dev.julioperez.nls.products.domain.search.Criteria;
import dev.julioperez.nls.products.domain.search.SearchSchema;

public interface SearchDecisionEngine {
    Criteria interpret(String message, SearchSchema schema);
}
