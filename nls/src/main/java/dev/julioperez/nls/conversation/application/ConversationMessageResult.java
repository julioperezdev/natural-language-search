package dev.julioperez.nls.conversation.application;

import dev.julioperez.nls.products.domain.search.Criteria;
import dev.julioperez.nls.products.domain.search.ProductSearchPage;
import dev.julioperez.nls.productsearch.domain.ProductSearchAnswerOutcome;
import java.util.UUID;

public record ConversationMessageResult(
        UUID conversationId,
        ProductSearchAnswerOutcome outcome,
        String reply,
        ProductSearchPage results,
        Criteria criteria,
        int retainedMessages,
        int maximumContextMessages) {
}
