package dev.julioperez.nls.conversation.application;

import dev.julioperez.nls.products.application.SearchConversationContext;
import dev.julioperez.nls.products.domain.search.Criteria;
import java.util.ArrayList;
import java.util.List;

/** Durable search state for one channel conversation. */
public record ConversationSearchState(
        Criteria currentCriteria,
        long nextSearchSequence,
        List<SearchConversationContext.SearchSnapshot> searchSnapshots,
        Long contextStartSequenceExclusive) {
    public static final int MAX_SEARCH_SNAPSHOTS = 10;

    public ConversationSearchState {
        searchSnapshots = searchSnapshots == null ? List.of() : List.copyOf(searchSnapshots);
        if (nextSearchSequence < 0) {
            throw new IllegalArgumentException("Search sequence cannot be negative.");
        }
        contextStartSequenceExclusive = contextStartSequenceExclusive == null ? 0L : contextStartSequenceExclusive;
        if (contextStartSequenceExclusive < 0) {
            throw new IllegalArgumentException("Conversation context sequence cannot be negative.");
        }
    }

    public static ConversationSearchState empty(Criteria criteria) {
        return new ConversationSearchState(criteria, 0, List.of(), 0L);
    }

    public ConversationSearchState withContextStartSequenceExclusive(long sequence) {
        return new ConversationSearchState(currentCriteria, nextSearchSequence, searchSnapshots, sequence);
    }

    public ConversationSearchState withCurrentCriteria(Criteria criteria) {
        return new ConversationSearchState(
                criteria, nextSearchSequence, searchSnapshots, contextStartSequenceExclusive);
    }

    public ConversationSearchState recordSearch(Criteria criteria, long totalResults) {
        long sequence = nextSearchSequence + 1;
        List<SearchConversationContext.SearchSnapshot> next = new ArrayList<>(searchSnapshots);
        next.add(new SearchConversationContext.SearchSnapshot(sequence, criteria, totalResults));
        if (next.size() > MAX_SEARCH_SNAPSHOTS) {
            SearchConversationContext.SearchSnapshot first = next.getFirst();
            next = new ArrayList<>(next.subList(next.size() - (MAX_SEARCH_SNAPSHOTS - 1), next.size()));
            next.addFirst(first);
        }
        return new ConversationSearchState(criteria, sequence, next, contextStartSequenceExclusive);
    }
}
