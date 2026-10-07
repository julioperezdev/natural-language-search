package dev.julioperez.nls.conversation.infrastructure.whatsapp;

import dev.julioperez.nls.infrastructure.logging.RequestLogContext;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public final class WhatsAppInboundPayloadParser {
    private static final Logger log = LoggerFactory.getLogger(WhatsAppInboundPayloadParser.class);
    private final ObjectMapper objectMapper;
    private final WhatsAppProperties properties;

    public WhatsAppInboundPayloadParser(ObjectMapper objectMapper, WhatsAppProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public List<WhatsAppInboundTextMessage> parse(byte[] rawBody) {
        ParseCounts counts = new ParseCounts();
        try {
            JsonNode root = objectMapper.readTree(rawBody);
            if (root == null || !"whatsapp_business_account".equals(root.path("object").asText())) {
                counts.unexpectedObject = 1;
                logSummary(counts);
                return List.of();
            }
            List<WhatsAppInboundTextMessage> messages = new ArrayList<>();
            for (JsonNode entry : root.path("entry")) {
                if (!matches(properties.businessAccountId(), entry.path("id").asText())) {
                    counts.businessAccountMismatchEntries++;
                    continue;
                }
                for (JsonNode change : entry.path("changes")) {
                    if (!"messages".equals(change.path("field").asText())) {
                        counts.nonMessageChanges++;
                        continue;
                    }
                    counts.messageChanges++;
                    JsonNode value = change.path("value");
                    if (!matches(properties.phoneNumberId(), value.path("metadata").path("phone_number_id").asText())) {
                        counts.phoneNumberMismatchChanges++;
                        continue;
                    }
                    for (JsonNode message : value.path("messages")) {
                        addText(messages, message, counts);
                    }
                }
            }
            counts.acceptedMessages = messages.size();
            logSummary(counts);
            return List.copyOf(messages);
        } catch (JacksonException exception) {
            throw new WhatsAppPayloadException();
        }
    }

    private void addText(List<WhatsAppInboundTextMessage> messages, JsonNode message, ParseCounts counts) {
        if (!"text".equals(message.path("type").asText())) {
            counts.unsupportedMessageTypes++;
            return;
        }
        String id = message.path("id").asText("");
        String sender = message.path("from").asText("");
        String body = message.path("text").path("body").asText("");
        if (id.isBlank() || sender.isBlank() || body.isBlank()) {
            counts.incompleteMessages++;
            return;
        }
        if (!isBlank(properties.allowedRecipient()) && !constantTimeEquals(properties.allowedRecipient().strip(), sender)) {
            counts.recipientAllowListDrops++;
            return;
        }
        messages.add(new WhatsAppInboundTextMessage(id, sender, body));
    }

    private static void logSummary(ParseCounts counts) {
        String result = counts.acceptedMessages > 0 ? "MATCHED" : counts.primaryNoMatchReason();
        String message = "WHATSAPP_PAYLOAD_PARSE_SUMMARY requestId={} result={} acceptedMessages={} "
                + "businessAccountMismatchEntries={} phoneNumberMismatchChanges={} unsupportedMessageTypes={} "
                + "incompleteMessages={} recipientAllowListDrops={} nonMessageChanges={}";
        if (counts.messageChanges > 0 || counts.unexpectedObject > 0 || counts.businessAccountMismatchEntries > 0) {
            log.info(message, RequestLogContext.requestId(), result, counts.acceptedMessages,
                    counts.businessAccountMismatchEntries, counts.phoneNumberMismatchChanges,
                    counts.unsupportedMessageTypes, counts.incompleteMessages,
                    counts.recipientAllowListDrops, counts.nonMessageChanges);
        } else {
            log.debug(message, RequestLogContext.requestId(), result, counts.acceptedMessages,
                    counts.businessAccountMismatchEntries, counts.phoneNumberMismatchChanges,
                    counts.unsupportedMessageTypes, counts.incompleteMessages,
                    counts.recipientAllowListDrops, counts.nonMessageChanges);
        }
    }

    private static final class ParseCounts {
        private int acceptedMessages;
        private int businessAccountMismatchEntries;
        private int phoneNumberMismatchChanges;
        private int unsupportedMessageTypes;
        private int incompleteMessages;
        private int recipientAllowListDrops;
        private int nonMessageChanges;
        private int messageChanges;
        private int unexpectedObject;

        private String primaryNoMatchReason() {
            if (unexpectedObject > 0) return "UNEXPECTED_OBJECT";
            if (businessAccountMismatchEntries > 0) return "BUSINESS_ACCOUNT_MISMATCH";
            if (phoneNumberMismatchChanges > 0) return "PHONE_NUMBER_MISMATCH";
            if (recipientAllowListDrops > 0) return "RECIPIENT_NOT_ALLOWED";
            if (unsupportedMessageTypes > 0) return "UNSUPPORTED_MESSAGE_TYPE";
            if (incompleteMessages > 0) return "INCOMPLETE_MESSAGE";
            return "NO_MESSAGES_FIELD";
        }
    }

    private static boolean matches(String configuredValue, String payloadValue) {
        return isBlank(configuredValue) || constantTimeEquals(configuredValue.strip(), payloadValue);
    }

    private static boolean constantTimeEquals(String first, String second) {
        return second != null && MessageDigest.isEqual(
                first.getBytes(StandardCharsets.UTF_8), second.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
