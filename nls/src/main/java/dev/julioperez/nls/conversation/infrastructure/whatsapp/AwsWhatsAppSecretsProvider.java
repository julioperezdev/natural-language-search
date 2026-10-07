package dev.julioperez.nls.conversation.infrastructure.whatsapp;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Loads Meta credentials from Secrets Manager and caches them only in process memory. */
@Component
public final class AwsWhatsAppSecretsProvider implements WhatsAppSecretsProvider {
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final SecretsManagerClient client;
    private final ObjectMapper objectMapper;
    private final String secretId;
    private final Clock clock;
    private volatile CachedSecrets cached;

    @Autowired
    public AwsWhatsAppSecretsProvider(
            SecretsManagerClient client,
            ObjectMapper objectMapper,
            WhatsAppProperties properties) {
        this(client, objectMapper, properties.secretId(), Clock.systemUTC());
    }

    AwsWhatsAppSecretsProvider(
            SecretsManagerClient client,
            ObjectMapper objectMapper,
            String secretId,
            Clock clock) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.secretId = secretId;
        this.clock = clock;
    }

    @Override
    public WhatsAppSecrets get() {
        Instant now = clock.instant();
        CachedSecrets current = cached;
        if (usable(current, now)) {
            return current.value();
        }
        synchronized (this) {
            now = clock.instant();
            current = cached;
            if (usable(current, now)) {
                return current.value();
            }
            try {
                String secretString = client.getSecretValue(GetSecretValueRequest.builder()
                                .secretId(secretId)
                                .build())
                        .secretString();
                JsonNode root = objectMapper.readTree(secretString);
                WhatsAppSecrets loaded = new WhatsAppSecrets(
                        required(root, "access-token", "access_token", "accessToken"),
                        required(root, "verify-token", "verify_token", "verifyToken"),
                        required(root, "app-secret", "app_secret", "appSecret"));
                cached = new CachedSecrets(loaded, now.plus(CACHE_TTL));
                return loaded;
            } catch (RuntimeException exception) {
                // Secret contents and AWS diagnostics must not escape into logs or API responses.
                throw new WhatsAppConfigurationUnavailableException();
            }
        }
    }

    private static String required(JsonNode root, String... candidates) {
        for (String candidate : candidates) {
            JsonNode value = root == null ? null : root.get(candidate);
            if (value != null && value.isString() && !value.stringValue().isBlank()) {
                return value.stringValue().trim();
            }
        }
        throw new IllegalStateException();
    }

    private static boolean usable(CachedSecrets value, Instant now) {
        return value != null && now.isBefore(value.expiresAt());
    }

    private record CachedSecrets(WhatsAppSecrets value, Instant expiresAt) {
    }
}
