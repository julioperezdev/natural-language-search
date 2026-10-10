package dev.julioperez.nls.conversation.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.julioperez.nls.products.application.SearchConversationContext;
import dev.julioperez.nls.products.domain.search.Criteria;
import dev.julioperez.nls.products.domain.search.Filter;
import dev.julioperez.nls.products.domain.search.FilterOperator;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ConversationStateCodecTest {
    private final ConversationStateCodec codec = new ConversationStateCodec(JsonMapper.builder().build());

    @Test
    void readsLegacyCriteriaOnlyStateAndWritesSnapshotsInTheNewEnvelope() {
        Criteria criteria = criteria("REMERAS");
        ConversationSearchState legacy = codec.decodeState(codec.encodeCriteria(criteria), 10);

        assertThat(legacy.currentCriteria()).isEqualTo(criteria);
        assertThat(legacy.searchSnapshots()).isEmpty();
        assertThat(legacy.contextStartSequenceExclusive()).isZero();

        ConversationSearchState state = legacy.withContextStartSequenceExclusive(7).recordSearch(criteria, 4);
        ConversationSearchState decoded = codec.decodeState(codec.encodeState(state), 10);

        assertThat(decoded).isEqualTo(state);
        assertThat(decoded.contextStartSequenceExclusive()).isEqualTo(7);
        assertThat(decoded.searchSnapshots()).containsExactly(
                new SearchConversationContext.SearchSnapshot(1, criteria, 4));
    }

    @Test
    void readsStructuredStateWrittenBeforeTheContextBoundaryWasAdded() {
        ConversationSearchState decoded = codec.decodeState("""
                {
                  "currentCriteria": {"filters": [], "order": null, "limit": 10, "offset": 0},
                  "nextSearchSequence": 3,
                  "searchSnapshots": []
                }
                """, 10);

        assertThat(decoded.nextSearchSequence()).isEqualTo(3);
        assertThat(decoded.contextStartSequenceExclusive()).isZero();
    }

    @Test
    void retainsTheFirstSearchAndTheMostRecentSnapshotsWithinTheBound() {
        ConversationSearchState state = ConversationSearchState.empty(codec.emptyCriteria(10));
        for (int sequence = 1; sequence <= 12; sequence++) {
            state = state.recordSearch(criteria("CATEGORY_" + sequence), sequence);
        }

        assertThat(state.searchSnapshots()).hasSize(ConversationSearchState.MAX_SEARCH_SNAPSHOTS);
        assertThat(state.searchSnapshots().getFirst().sequence()).isEqualTo(1);
        assertThat(state.searchSnapshots().getLast().sequence()).isEqualTo(12);
        assertThat(state.nextSearchSequence()).isEqualTo(12);
    }

    private static Criteria criteria(String value) {
        return new Criteria(List.of(new Filter("category", FilterOperator.EQUALS, value)), null, 10, 0);
    }
}
