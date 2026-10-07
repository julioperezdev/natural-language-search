package dev.julioperez.nls.conversation.infrastructure.http;

import dev.julioperez.nls.conversation.application.ConversationMessageOutcome;
import dev.julioperez.nls.conversation.application.ConversationMessageResult;
import dev.julioperez.nls.products.domain.search.Criteria;
import dev.julioperez.nls.products.domain.search.ProductSearchPage;
import java.util.UUID;

public record ConversationMessageResponse(
        UUID conversationId,
        ConversationMessageOutcome outcome,
        String reply,
        ProductSearchPage results,
        Criteria criteria,
        ContextWindow context) {
    public static ConversationMessageResponse from(ConversationMessageResult result) {
        return new ConversationMessageResponse(
                result.conversationId(), result.outcome(), result.reply(), result.results(), result.criteria(),
                new ContextWindow(result.retainedMessages(), result.maximumContextMessages()));
    }

    public record ContextWindow(int retainedMessages, int maximumMessages) {
    }
}
