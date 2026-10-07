package dev.julioperez.nls.conversation.infrastructure.whatsapp;

import dev.julioperez.nls.conversation.application.ConversationMessageCommand;
import dev.julioperez.nls.conversation.application.ConversationMessageService;
import dev.julioperez.nls.conversation.domain.ConversationChannel;
import dev.julioperez.nls.conversation.domain.ConversationIdentity;
import dev.julioperez.nls.infrastructure.logging.RequestLogContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "nls.whatsapp", name = "enabled", havingValue = "true")
public final class WhatsAppInboundConversationHandler {
    private static final Logger log = LoggerFactory.getLogger(WhatsAppInboundConversationHandler.class);
    private final WhatsAppProperties properties;
    private final ConversationMessageService conversations;
    private final WhatsAppMessageSender sender;

    public WhatsAppInboundConversationHandler(
            WhatsAppProperties properties,
            ConversationMessageService conversations,
            WhatsAppMessageSender sender) {
        this.properties = properties;
        this.conversations = conversations;
        this.sender = sender;
    }

    public void handle(WhatsAppInboundTextMessage message) {
        String requestId = RequestLogContext.requestId();
        long startedAt = System.nanoTime();
        log.info("WHATSAPP_INBOUND_PROCESSING_STARTED requestId={}", requestId);
        ConversationMessageCommand command = new ConversationMessageCommand(
                new ConversationIdentity(ConversationChannel.WHATSAPP, properties.phoneNumberId(), message.senderId()),
                message.providerMessageId(), message.text());
        try {
            boolean resetCommand = WhatsAppResetCommand.matches(message.text());
            if (resetCommand) {
                log.info("WHATSAPP_RESET_COMMAND_RECEIVED requestId={}", requestId);
            }
            var result = resetCommand ? conversations.resetContext(command) : conversations.handle(command);
            long totalResults = result.results() == null ? 0 : result.results().total();
            log.info("WHATSAPP_CONVERSATION_COMPLETED requestId={} outcome={} totalResults={} retainedMessages={}",
                    requestId, result.outcome(), totalResults, result.retainedMessages());
            if (result.reply() == null || result.reply().isBlank()) {
                log.warn("WHATSAPP_REPLY_NOT_SENT requestId={} reason=empty_reply", requestId);
                return;
            }
            sender.sendText(message.senderId(), result.reply());
            log.info("WHATSAPP_INBOUND_PROCESSING_COMPLETED requestId={} durationMs={}",
                    requestId, elapsedMillis(startedAt));
        } catch (RuntimeException exception) {
            log.error("WHATSAPP_INBOUND_PROCESSING_FAILED requestId={} errorType={} durationMs={}",
                    requestId, exception.getClass().getSimpleName(), elapsedMillis(startedAt));
            throw exception;
        }
    }

    private static long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
