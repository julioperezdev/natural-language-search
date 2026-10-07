package dev.julioperez.nls.conversation.application;

import dev.julioperez.nls.products.domain.search.Criteria;
import dev.julioperez.nls.products.domain.search.ProductSearchPage;
import java.util.UUID;

public record ConversationMessageResult(
        UUID conversationId,
        ConversationMessageOutcome outcome,
        String reply,
        ProductSearchPage results,
        Criteria criteria,
        int retainedMessages,
        int maximumContextMessages) {
}
