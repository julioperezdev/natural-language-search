package dev.julioperez.nls.conversation.infrastructure.whatsapp;

public record WhatsAppSecrets(String accessToken, String verifyToken, String appSecret) {
    @Override
    public String toString() {
        return "WhatsAppSecrets[values=REDACTED]";
    }
}
