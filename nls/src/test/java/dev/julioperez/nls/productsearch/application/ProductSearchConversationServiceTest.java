package dev.julioperez.nls.productsearch.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.julioperez.nls.products.application.ProductSearchService;
import dev.julioperez.nls.products.application.SearchDecisionEngine;
import dev.julioperez.nls.products.application.SearchConversationContext;
import dev.julioperez.nls.products.application.SearchInterpretationFailedException;
import dev.julioperez.nls.products.domain.search.Criteria;
import dev.julioperez.nls.products.domain.search.Filter;
import dev.julioperez.nls.products.domain.search.FilterOperator;
import dev.julioperez.nls.products.domain.search.ProductSearchRepository;
import dev.julioperez.nls.products.domain.search.SearchPaginationSchema;
import dev.julioperez.nls.products.domain.search.SearchSchema;
import dev.julioperez.nls.productsearch.domain.ProductSearchAnswerOutcome;
import dev.julioperez.nls.productsearchresponses.application.ProductSearchResponseService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ProductSearchConversationServiceTest {
    @Test
    void clarifiesWhatCanBeChangedAndPreservesCurrentFiltersWhenInterpretationIsUncertain() {
        ProductSearchRepository repository = mock(ProductSearchRepository.class);
        SearchDecisionEngine decisionEngine = mock(SearchDecisionEngine.class);
        ProductSearchResponseService humanizer = mock(ProductSearchResponseService.class);
        SearchSchema schema = new SearchSchema("product-search", List.of(), new SearchPaginationSchema(10, 50));
        Criteria current = new Criteria(List.of(
                new Filter("category", FilterOperator.EQUALS, "REMERAS"),
                new Filter("color", FilterOperator.EQUALS, "BLACK")), null, 10, 0);
        when(repository.schema()).thenReturn(schema);
        when(decisionEngine.interpretTurn("Tenes remeras?", schema, current, SearchConversationContext.empty()))
                .thenThrow(new SearchInterpretationFailedException(
                        SearchInterpretationFailedException.Reason.LOW_CONFIDENCE, "color"));

        ProductSearchConversationService service = new ProductSearchConversationService(
                new ProductSearchService(repository, Optional.of(decisionEngine)), humanizer);

        var answer = service.answer("Tenes remeras?", current);

        assertThat(answer.outcome()).isEqualTo(ProductSearchAnswerOutcome.NEEDS_CLARIFICATION);
        assertThat(answer.reply()).contains("Mantengo los filtros", "color", "talle", "precio");
        assertThat(answer.criteria()).isEqualTo(current);
        assertThat(answer.results()).isNull();
        verify(repository).schema();
        verifyNoInteractions(humanizer);
    }
}
