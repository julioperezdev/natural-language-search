package dev.julioperez.nls.conversation.infrastructure.whatsapp;

public final class WhatsAppPayloadException extends RuntimeException {
    public WhatsAppPayloadException() {
        super("WhatsApp webhook payload is invalid.");
    }
}
