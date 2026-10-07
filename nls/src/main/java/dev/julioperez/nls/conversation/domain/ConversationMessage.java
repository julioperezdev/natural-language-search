package dev.julioperez.nls.conversation.domain;

import java.time.Instant;
import java.util.UUID;

public record ConversationMessage(
        UUID id,
        UUID conversationId,
        long sequence,
        ConversationMessageDirection direction,
        String content,
        String providerMessageId,
        UUID replyToMessageId,
        Instant createdAt) {
}
