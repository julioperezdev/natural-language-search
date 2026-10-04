package dev.julioperez.nls.productsearch.application;

import dev.julioperez.nls.products.application.ProductSearchService;
import dev.julioperez.nls.products.application.SearchInterpretationFailedException;
import dev.julioperez.nls.products.domain.search.Criteria;
import dev.julioperez.nls.products.domain.search.ProductSearchPage;
import dev.julioperez.nls.productsearch.domain.ProductSearchAnswerOutcome;
import dev.julioperez.nls.productsearchresponses.application.ProductSearchResponseService;
import dev.julioperez.nls.productsearchresponses.domain.HumanizedProductSearchResponse;
import dev.julioperez.nls.productsearchresponses.domain.SearchProductFact;
import dev.julioperez.nls.productsearchresponses.domain.SearchResultFacts;
import dev.julioperez.nls.productsearchresponses.domain.SearchVariantFact;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ProductSearchConversationService {
    private final ProductSearchService productSearch;
    private final ProductSearchResponseService responseHumanizer;

    public ProductSearchConversationService(
            ProductSearchService productSearch,
            ProductSearchResponseService responseHumanizer) {
        this.productSearch = productSearch;
        this.responseHumanizer = responseHumanizer;
    }

    public ProductSearchAnswer answer(String message) {
        Criteria criteria;
        try {
            criteria = productSearch.interpret(message);
        } catch (SearchInterpretationFailedException exception) {
            return new ProductSearchAnswer(
                    ProductSearchAnswerOutcome.NEEDS_CLARIFICATION,
                    "No pude identificar con seguridad qué producto o característica buscás. ¿Podés darme un poco más de detalle?",
                    null);
        }
        ProductSearchPage results = productSearch.search(criteria);
        HumanizedProductSearchResponse response = responseHumanizer.humanize(message, facts(results));
        return new ProductSearchAnswer(
                ProductSearchAnswerOutcome.valueOf(response.outcome().name()), response.reply(), results);
    }

    private SearchResultFacts facts(ProductSearchPage results) {
        List<SearchProductFact> items = results.items().stream()
                .map(item -> new SearchProductFact(
                        item.id(), item.name(), item.category(), item.variants().stream()
                                .map(variant -> new SearchVariantFact(
                                        variant.id(), variant.color(), variant.size(), variant.price(), variant.stock()))
                                .toList()))
                .toList();
        return new SearchResultFacts(items, results.total(), results.limit(), results.offset());
    }
}
