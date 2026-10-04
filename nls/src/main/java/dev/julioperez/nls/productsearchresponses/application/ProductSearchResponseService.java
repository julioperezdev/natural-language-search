package dev.julioperez.nls.productsearchresponses.application;

import dev.julioperez.nls.productsearchresponses.domain.HumanizedProductSearchResponse;
import dev.julioperez.nls.productsearchresponses.domain.ProductSearchResponseHumanizer;
import dev.julioperez.nls.productsearchresponses.domain.SearchResultFacts;
import org.springframework.stereotype.Service;

@Service
public class ProductSearchResponseService {
    private final ProductSearchResponseHumanizer humanizer = new ProductSearchResponseHumanizer();

    public HumanizedProductSearchResponse humanize(String message, SearchResultFacts results) {
        return humanizer.humanize(message, results);
    }
}
