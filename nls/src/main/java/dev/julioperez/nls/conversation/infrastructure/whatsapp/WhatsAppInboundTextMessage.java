package dev.julioperez.nls.conversation.infrastructure.whatsapp;

public record WhatsAppInboundTextMessage(String providerMessageId, String senderId, String text) {
}
