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
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class ProductSearchConversationService {
    private static final Pattern RESET_CONTEXT = Pattern.compile(
            "(?iu)\\b(?:olvida(?:te)?\\s+(?:(?:lo\\s+)?anterior|todo)|"
                    + "empecemos?\\s+(?:de\\s+nuevo|desde\\s+cero)|"
                    + "arranquemos?\\s+(?:de\\s+nuevo|desde\\s+cero)|desde\\s+cero|"
                    + "limpia\\s+(?:los\\s+)?filtros|saca\\s+todos\\s+los\\s+filtros)\\b[,;:]?");
    private final ProductSearchService productSearch;
    private final ProductSearchResponseService responseHumanizer;

    public ProductSearchConversationService(
            ProductSearchService productSearch,
            ProductSearchResponseService responseHumanizer) {
        this.productSearch = productSearch;
        this.responseHumanizer = responseHumanizer;
    }

    public ProductSearchAnswer answer(String message) {
        return answer(message, new Criteria(List.of(), null, 10, 0));
    }

    public ProductSearchAnswer answer(String message, Criteria currentCriteria) {
        Criteria previous = currentCriteria == null
                ? new Criteria(List.of(), null, 10, 0)
                : currentCriteria;
        String interpretationMessage = message;
        var resetMatcher = RESET_CONTEXT.matcher(message);
        if (resetMatcher.find()) {
            previous = new Criteria(List.of(), null, previous.limit(), 0);
            interpretationMessage = resetMatcher.replaceAll(" ").strip();
        }
        if (interpretationMessage.isBlank()) {
            return clarification(previous);
        }

        Criteria criteria;
        try {
            criteria = productSearch.interpret(interpretationMessage, previous);
        } catch (SearchInterpretationFailedException exception) {
            return clarification(previous);
        }
        ProductSearchPage results = productSearch.search(criteria);
        HumanizedProductSearchResponse response = responseHumanizer.humanize(message, facts(results));
        return new ProductSearchAnswer(
                ProductSearchAnswerOutcome.valueOf(response.outcome().name()), response.reply(), results, criteria);
    }

    private ProductSearchAnswer clarification(Criteria criteria) {
        return new ProductSearchAnswer(
                ProductSearchAnswerOutcome.NEEDS_CLARIFICATION,
                "No pude identificar con seguridad qué producto o característica buscás. ¿Podés darme un poco más de detalle?",
                null,
                criteria);
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
