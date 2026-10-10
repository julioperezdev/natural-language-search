package dev.julioperez.nls.products.application;

import dev.julioperez.nls.products.domain.search.Criteria;
import java.util.List;

/** Prior conversation turns supplied only to resolve references in the latest search message. */
public record SearchConversationContext(
        List<Turn> turns,
        List<SearchSnapshot> searchSnapshots,
        String resolvedSearchReference) {
    private static final SearchConversationContext EMPTY = new SearchConversationContext(List.of(), List.of(), null);

    public SearchConversationContext {
        turns = turns == null ? List.of() : List.copyOf(turns);
        searchSnapshots = searchSnapshots == null ? List.of() : List.copyOf(searchSnapshots);
    }

    public SearchConversationContext(List<Turn> turns) {
        this(turns, List.of(), null);
    }

    public SearchConversationContext(List<Turn> turns, List<SearchSnapshot> searchSnapshots) {
        this(turns, searchSnapshots, null);
    }

    public static SearchConversationContext empty() {
        return EMPTY;
    }

    public boolean isEmpty() {
        return turns.isEmpty() && searchSnapshots.isEmpty() && resolvedSearchReference == null;
    }

    public enum Role {
        USER("user"),
        ASSISTANT("assistant");

        private final String wireValue;

        Role(String wireValue) {
            this.wireValue = wireValue;
        }

        public String wireValue() {
            return wireValue;
        }
    }

    public record Turn(Role role, String content) {
        public Turn {
            if (role == null || content == null || content.isBlank()) {
                throw new IllegalArgumentException("Conversation turns require a role and non-blank content.");
            }
        }
    }

    /** A previously validated search, ordered by its sequence within the current conversation. */
    public record SearchSnapshot(long sequence, Criteria criteria, long totalResults) {
        public SearchSnapshot {
            if (sequence < 1 || criteria == null || totalResults < 0) {
                throw new IllegalArgumentException("Search snapshots require a sequence, criteria, and result count.");
            }
        }

        public String reference() {
            return "SEARCH_" + sequence;
        }
    }
}
