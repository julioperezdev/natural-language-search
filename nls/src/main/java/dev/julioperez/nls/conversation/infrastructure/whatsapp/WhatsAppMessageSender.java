package dev.julioperez.nls.conversation.infrastructure.whatsapp;

public interface WhatsAppMessageSender {
    void sendText(String recipientId, String text);
}
