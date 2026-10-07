package dev.julioperez.nls.conversation.infrastructure.whatsapp;

public final class WhatsAppDeliveryException extends RuntimeException {
    public WhatsAppDeliveryException() {
        super("WhatsApp message delivery failed.");
    }
}
