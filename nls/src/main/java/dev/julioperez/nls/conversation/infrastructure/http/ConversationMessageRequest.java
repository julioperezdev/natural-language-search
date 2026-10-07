package dev.julioperez.nls.conversation.infrastructure.http;

import dev.julioperez.nls.conversation.domain.ConversationChannel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ConversationMessageRequest(
        @NotNull ConversationChannel channel,
        @NotBlank @Size(max = 160) String channelAccountId,
        @NotBlank @Size(max = 160) String participantId,
        @NotBlank @Size(max = 200) String messageId,
        @NotBlank @Size(max = 2_000) String message) {
}
