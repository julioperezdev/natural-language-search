package dev.julioperez.nls.conversation.infrastructure.whatsapp;

import dev.julioperez.nls.infrastructure.logging.RequestLogContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.UUID;
import org.slf4j.MDC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/webhook/whatsapp")
@ConditionalOnProperty(prefix = "nls.whatsapp", name = "enabled", havingValue = "true")
public class WhatsAppWebhookController {
    private static final Logger log = LoggerFactory.getLogger(WhatsAppWebhookController.class);
    private static final int MAX_WEBHOOK_BODY_BYTES = 1_048_576;

    private final WhatsAppSecretsProvider secretsProvider;
    private final WhatsAppWebhookSignatureVerifier signatureVerifier;
    private final WhatsAppInboundPayloadParser payloadParser;
    private final WhatsAppInboundConversationHandler handler;

    public WhatsAppWebhookController(
            WhatsAppSecretsProvider secretsProvider,
            WhatsAppWebhookSignatureVerifier signatureVerifier,
            WhatsAppInboundPayloadParser payloadParser,
            WhatsAppInboundConversationHandler handler) {
        this.secretsProvider = secretsProvider;
        this.signatureVerifier = signatureVerifier;
        this.payloadParser = payloadParser;
        this.handler = handler;
    }

    @GetMapping
    public ResponseEntity<String> verify(
            @RequestParam(name = "hub.mode", required = false) String mode,
            @RequestParam(name = "hub.verify_token", required = false) String verifyToken,
            @RequestParam(name = "hub.challenge", required = false) String challenge) {
        if (!"subscribe".equals(mode) || isBlank(verifyToken) || isBlank(challenge)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        try {
            if (!constantTimeEquals(verifyToken, secretsProvider.get().verifyToken())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            return ResponseEntity.ok(challenge);
        } catch (WhatsAppConfigurationUnavailableException exception) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
    }

    @PostMapping
    public ResponseEntity<Void> receive(
            @RequestBody byte[] rawBody,
            @RequestHeader(name = "X-Hub-Signature-256", required = false) String signature) {
        String requestId = RequestLogContext.requestId();
        if ("unavailable".equals(requestId)) {
            requestId = UUID.randomUUID().toString();
        }
        try (MDC.MDCCloseable ignored = MDC.putCloseable("requestId", requestId)) {
            return receiveWithRequestContext(rawBody, signature, requestId);
        }
    }

    private ResponseEntity<Void> receiveWithRequestContext(
            byte[] rawBody,
            String signature,
            String requestId) {
        log.info("WHATSAPP_WEBHOOK_RECEIVED requestId={} bodyBytes={}", requestId,
                rawBody == null ? 0 : rawBody.length);
        if (rawBody == null || rawBody.length > MAX_WEBHOOK_BODY_BYTES) {
            log.warn("WHATSAPP_WEBHOOK_REJECTED requestId={} reason=payload_too_large", requestId);
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).build();
        }
        final WhatsAppSecrets secrets;
        try {
            secrets = secretsProvider.get();
        } catch (WhatsAppConfigurationUnavailableException exception) {
            log.error("WHATSAPP_WEBHOOK_REJECTED requestId={} reason=secrets_unavailable errorType={}",
                    requestId, exception.getClass().getSimpleName());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        if (!signatureVerifier.isValid(rawBody, signature, secrets.appSecret())) {
            log.warn("WHATSAPP_WEBHOOK_REJECTED requestId={} reason=invalid_signature", requestId);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        log.info("WHATSAPP_WEBHOOK_SIGNATURE_VALID requestId={}", requestId);
        final List<WhatsAppInboundTextMessage> messages;
        try {
            messages = payloadParser.parse(rawBody);
        } catch (WhatsAppPayloadException exception) {
            log.warn("WHATSAPP_WEBHOOK_REJECTED requestId={} reason=invalid_payload", requestId);
            return ResponseEntity.badRequest().build();
        }
        log.info("WHATSAPP_WEBHOOK_MESSAGES_PARSED requestId={} messageCount={}", requestId, messages.size());
        if (messages.isEmpty()) {
            log.info("WHATSAPP_WEBHOOK_ACKNOWLEDGED requestId={} result=no_matching_text_messages", requestId);
            return ResponseEntity.ok().build();
        }
        try {
            for (WhatsAppInboundTextMessage message : messages) {
                handler.handle(message);
            }
            log.info("WHATSAPP_WEBHOOK_COMPLETED requestId={} processedMessageCount={}", requestId, messages.size());
            return ResponseEntity.ok().build();
        } catch (RuntimeException exception) {
            // Meta can retry this delivery. Never log the body, phone number, token, or raw provider response.
            log.error("WHATSAPP_WEBHOOK_PROCESSING_FAILED requestId={} errorType={}",
                    requestId, exception.getClass().getSimpleName());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private static boolean constantTimeEquals(String first, String second) {
        return !isBlank(first) && !isBlank(second) && MessageDigest.isEqual(
                first.getBytes(StandardCharsets.UTF_8), second.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
