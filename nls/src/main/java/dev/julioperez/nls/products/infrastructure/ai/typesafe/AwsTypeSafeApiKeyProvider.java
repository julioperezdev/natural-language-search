package dev.julioperez.nls.products.infrastructure.ai.typesafe;

import dev.julioperez.nls.products.application.SearchInterpretationUnavailableException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Reads the TypeSafe credential from AWS and keeps it in process memory for five minutes. */
@Component
public final class AwsTypeSafeApiKeyProvider implements TypeSafeApiKeyProvider {
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);
    private static final String SECRET_PROPERTY = "API_KEY";

    private final SecretsManagerClient secretsManagerClient;
    private final ObjectMapper objectMapper;
    private final TypeSafeSearchProperties properties;
    private final Clock clock;
    private volatile CachedApiKey cachedApiKey;

    @Autowired
    public AwsTypeSafeApiKeyProvider(
            SecretsManagerClient secretsManagerClient,
            ObjectMapper objectMapper,
            TypeSafeSearchProperties properties) {
        this(secretsManagerClient, objectMapper, properties, Clock.systemUTC());
    }

    AwsTypeSafeApiKeyProvider(
            SecretsManagerClient secretsManagerClient,
            ObjectMapper objectMapper,
            TypeSafeSearchProperties properties,
            Clock clock) {
        this.secretsManagerClient = secretsManagerClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public String getApiKey() {
        Instant now = clock.instant();
        CachedApiKey current = cachedApiKey;
        if (isUsable(current, now)) {
            return current.value();
        }

        synchronized (this) {
            now = clock.instant();
            current = cachedApiKey;
            if (isUsable(current, now)) {
                return current.value();
            }

            try {
                String secretString = secretsManagerClient.getSecretValue(GetSecretValueRequest.builder()
                                .secretId(properties.secretId())
                                .build())
                        .secretString();
                JsonNode secret = objectMapper.readTree(secretString);
                JsonNode apiKey = secret == null ? null : secret.get(SECRET_PROPERTY);
                if (apiKey == null || !apiKey.isString() || apiKey.stringValue().isBlank()) {
                    throw new SearchInterpretationUnavailableException();
                }

                String value = apiKey.stringValue().trim();
                cachedApiKey = new CachedApiKey(value, now.plus(CACHE_TTL));
                return value;
            } catch (SearchInterpretationUnavailableException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                // Do not expose AWS SDK errors, response payloads, or secret contents to API callers.
                throw new SearchInterpretationUnavailableException();
            }
        }
    }

    private static boolean isUsable(CachedApiKey cached, Instant now) {
        return cached != null && now.isBefore(cached.expiresAt());
    }

    private record CachedApiKey(String value, Instant expiresAt) {
    }
}
