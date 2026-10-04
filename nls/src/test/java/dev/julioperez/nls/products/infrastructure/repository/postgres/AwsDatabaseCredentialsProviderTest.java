package dev.julioperez.nls.products.infrastructure.repository.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import tools.jackson.databind.json.JsonMapper;

class AwsDatabaseCredentialsProviderTest {
    private static final String SECRET_ID = "wcs/prod/database";

    @Test
    void readsTheDatabaseConnectionFieldsFromTheConfiguredSecret() {
        SecretsManagerClient client = mock(SecretsManagerClient.class);
        when(client.getSecretValue(any(GetSecretValueRequest.class))).thenReturn(
                GetSecretValueResponse.builder()
                        .secretString("""
                                {"jdbc_url":"jdbc:postgresql://db.example:5432/tesis?sslmode=require",
                                 "username":"synthetic-user","password":"synthetic-password"}
                                """)
                        .build());
        AwsDatabaseCredentialsProvider provider = new AwsDatabaseCredentialsProvider(
                client, JsonMapper.builder().build());

        var result = provider.load(SECRET_ID);

        assertThat(result.jdbcUrl()).startsWith("jdbc:postgresql://db.example:5432/tesis");
        assertThat(result.username()).isEqualTo("synthetic-user");
        assertThat(result.password()).isEqualTo("synthetic-password");
        assertThat(result.toString()).doesNotContain("synthetic-password", "synthetic-user", "db.example");
    }

    @Test
    void sanitizesSecretManagerAndPayloadErrors() {
        SecretsManagerClient client = mock(SecretsManagerClient.class);
        when(client.getSecretValue(any(GetSecretValueRequest.class)))
                .thenThrow(new IllegalStateException("synthetic-private-database-diagnostic"));
        AwsDatabaseCredentialsProvider provider = new AwsDatabaseCredentialsProvider(
                client, JsonMapper.builder().build());

        assertThatThrownBy(() -> provider.load(SECRET_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Database connection settings are unavailable.")
                .hasNoCause();
    }

    @Test
    void rejectsNonPostgresOrIncompleteSecrets() {
        SecretsManagerClient client = mock(SecretsManagerClient.class);
        when(client.getSecretValue(any(GetSecretValueRequest.class))).thenReturn(
                GetSecretValueResponse.builder()
                        .secretString("{\"jdbc_url\":\"jdbc:mysql://private-host/db\","
                                + "\"username\":\"synthetic-user\",\"password\":\"synthetic-password\"}")
                        .build());
        AwsDatabaseCredentialsProvider provider = new AwsDatabaseCredentialsProvider(
                client, JsonMapper.builder().build());

        assertThatThrownBy(() -> provider.load(SECRET_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Database connection settings are unavailable.")
                .hasNoCause();
    }
}
