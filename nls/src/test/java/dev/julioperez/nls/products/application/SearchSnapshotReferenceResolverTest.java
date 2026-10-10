package dev.julioperez.nls.products.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.julioperez.nls.products.domain.search.Criteria;
import dev.julioperez.nls.products.domain.search.Filter;
import dev.julioperez.nls.products.domain.search.FilterOperator;
import java.util.List;
import org.junit.jupiter.api.Test;

class SearchSnapshotReferenceResolverTest {
    private static final List<SearchConversationContext.SearchSnapshot> SNAPSHOTS = List.of(
            snapshot(1, "BUZOS"), snapshot(2, "REMERAS"), snapshot(3, "PANTALONES"));

    @Test
    void resolvesTheFirstSearchBeforeApplyingTheLatestTurn() {
        var selected = SearchSnapshotReferenceResolver.resolve(
                "Volvamos a lo primero, pero en M", SNAPSHOTS);

        assertThat(selected).isPresent();
        assertThat(selected.orElseThrow().reference()).isEqualTo("SEARCH_1");
    }

    @Test
    void resolvesAnExplicitOrdinalAndTheSearchBeforeTheCurrentOne() {
        assertThat(SearchSnapshotReferenceResolver.resolve("Retomemos la segunda búsqueda", SNAPSHOTS))
                .map(SearchConversationContext.SearchSnapshot::reference)
                .contains("SEARCH_2");
        assertThat(SearchSnapshotReferenceResolver.resolve("Volvamos a la anterior", SNAPSHOTS))
                .map(SearchConversationContext.SearchSnapshot::reference)
                .contains("SEARCH_2");
    }

    @Test
    void doesNotTreatAnUnqualifiedOptionReferenceAsASearchReference() {
        assertThat(SearchSnapshotReferenceResolver.isExplicitReference("Me quedo con la primera opción"))
                .isFalse();
        assertThat(SearchSnapshotReferenceResolver.resolve("Me quedo con la primera opción", SNAPSHOTS))
                .isEmpty();
    }

    @Test
    void reportsAnExplicitReferenceEvenWhenThatSnapshotIsNoLongerAvailable() {
        assertThat(SearchSnapshotReferenceResolver.isExplicitReference("Volvamos a la anterior"))
                .isTrue();
        assertThat(SearchSnapshotReferenceResolver.resolve("Volvamos a la anterior", List.of(snapshot(1, "BUZOS"))))
                .isEmpty();
    }

    private static SearchConversationContext.SearchSnapshot snapshot(long sequence, String category) {
        return new SearchConversationContext.SearchSnapshot(
                sequence,
                new Criteria(List.of(new Filter("category", FilterOperator.EQUALS, category)), null, 10, 0),
                1);
    }
}
