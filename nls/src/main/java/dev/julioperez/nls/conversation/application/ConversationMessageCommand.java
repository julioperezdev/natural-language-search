package dev.julioperez.nls.conversation.application;

import dev.julioperez.nls.conversation.domain.ConversationIdentity;

public record ConversationMessageCommand(
        ConversationIdentity identity,
        String providerMessageId,
        String message) {
}
