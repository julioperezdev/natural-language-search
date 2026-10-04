package dev.julioperez.nls.products.infrastructure.repository.postgres;

import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public final class AwsDatabaseCredentialsProvider {
    private final SecretsManagerClient secretsManagerClient;
    private final ObjectMapper objectMapper;

    public AwsDatabaseCredentialsProvider(
            SecretsManagerClient secretsManagerClient,
            ObjectMapper objectMapper) {
        this.secretsManagerClient = secretsManagerClient;
        this.objectMapper = objectMapper;
    }

    public DatabaseConnectionSecret load(String secretId) {
        try {
            String secretString = secretsManagerClient.getSecretValue(GetSecretValueRequest.builder()
                            .secretId(secretId)
                            .build())
                    .secretString();
            JsonNode secret = objectMapper.readTree(secretString);
            String jdbcUrl = text(secret, "jdbc_url");
            String username = text(secret, "username");
            String password = text(secret, "password");
            if (!jdbcUrl.startsWith("jdbc:postgresql://")) {
                throw new IllegalStateException();
            }
            return new DatabaseConnectionSecret(jdbcUrl, username, password);
        } catch (RuntimeException exception) {
            // Keep secret content, URLs, credentials, and AWS diagnostics out of logs and API errors.
            throw new IllegalStateException("Database connection settings are unavailable.");
        }
    }

    private static String text(JsonNode secret, String name) {
        JsonNode value = secret == null ? null : secret.get(name);
        if (value == null || !value.isString() || value.stringValue().isBlank()) {
            throw new IllegalStateException();
        }
        return value.stringValue();
    }

    public record DatabaseConnectionSecret(String jdbcUrl, String username, String password) {
        @Override
        public String toString() {
            return "DatabaseConnectionSecret[values=REDACTED]";
        }
    }
}
