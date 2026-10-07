package dev.julioperez.nls.conversation.application;

import dev.julioperez.nls.productsearch.domain.ProductSearchAnswerOutcome;

public enum ConversationMessageOutcome {
    RESULTS,
    NO_RESULTS,
    NEEDS_CLARIFICATION,
    CONTEXT_RESET;

    public static ConversationMessageOutcome from(ProductSearchAnswerOutcome outcome) {
        return switch (outcome) {
            case RESULTS -> RESULTS;
            case NO_RESULTS -> NO_RESULTS;
            case NEEDS_CLARIFICATION -> NEEDS_CLARIFICATION;
        };
    }
}
