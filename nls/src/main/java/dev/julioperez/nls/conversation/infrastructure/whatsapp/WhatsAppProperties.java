package dev.julioperez.nls.conversation.infrastructure.whatsapp;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "nls.whatsapp")
public record WhatsAppProperties(
        boolean enabled,
        String secretId,
        String graphApiBaseUrl,
        String graphApiVersion,
        String businessAccountId,
        String phoneNumberId,
        String allowedRecipient,
        Duration connectTimeout,
        Duration readTimeout) {
    public WhatsAppProperties {
        if (enabled && (isBlank(secretId)
                || isBlank(graphApiBaseUrl)
                || !graphApiBaseUrl.startsWith("https://")
                || isBlank(graphApiVersion)
                || !graphApiVersion.matches("v[0-9]+\\.[0-9]+")
                || isBlank(businessAccountId)
                || isBlank(phoneNumberId))) {
            throw new IllegalArgumentException("WhatsApp integration is enabled but its non-secret configuration is incomplete.");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
