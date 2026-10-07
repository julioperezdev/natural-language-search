package dev.julioperez.nls.conversation.infrastructure.http;

import dev.julioperez.nls.conversation.application.ConversationMessageCommand;
import dev.julioperez.nls.conversation.application.ConversationMessageService;
import dev.julioperez.nls.conversation.domain.ConversationIdentity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/conversations")
@Tag(name = "Conversations", description = "Persistent search context for channel conversations.")
public class ConversationMessageController {
    private final ConversationMessageService conversations;

    public ConversationMessageController(ConversationMessageService conversations) {
        this.conversations = conversations;
    }

    @PostMapping("/messages")
    @Operation(
            summary = "Process a message in its channel conversation",
            description = "Resolves a conversation from the trusted channel account and participant identity, "
                    + "then applies the message to the existing product search context.")
    public ConversationMessageResponse handle(@Valid @RequestBody ConversationMessageRequest request) {
        ConversationMessageCommand command = new ConversationMessageCommand(
                new ConversationIdentity(request.channel(), request.channelAccountId(), request.participantId()),
                request.messageId(), request.message());
        return ConversationMessageResponse.from(conversations.handle(command));
    }
}
