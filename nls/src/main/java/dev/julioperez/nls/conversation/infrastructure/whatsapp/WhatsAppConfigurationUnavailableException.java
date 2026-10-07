package dev.julioperez.nls.conversation.infrastructure.whatsapp;

public final class WhatsAppConfigurationUnavailableException extends RuntimeException {
    public WhatsAppConfigurationUnavailableException() {
        super("WhatsApp integration configuration is unavailable.");
    }
}
