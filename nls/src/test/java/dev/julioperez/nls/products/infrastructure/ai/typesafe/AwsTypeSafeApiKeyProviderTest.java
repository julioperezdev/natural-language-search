package dev.julioperez.nls.products.infrastructure.ai.typesafe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.julioperez.nls.products.application.SearchInterpretationUnavailableException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import tools.jackson.databind.json.JsonMapper;

class AwsTypeSafeApiKeyProviderTest {
    private static final String SECRET_ID = "wcs/prod/typesafe";
    private static final String SYNTHETIC_SECRET = "synthetic-api-key-for-unit-test";
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-09-30T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void readsApiKeyAndCachesItInMemoryForFiveMinutes() {
        SecretsManagerClient client = mock(SecretsManagerClient.class);
        when(client.getSecretValue(any(GetSecretValueRequest.class))).thenReturn(
                GetSecretValueResponse.builder()
                        .secretString("{\"API_KEY\":\"" + SYNTHETIC_SECRET + "\"}")
                        .build());
        AwsTypeSafeApiKeyProvider provider = provider(client, FIXED_CLOCK);

        assertThat(provider.getApiKey()).isEqualTo(SYNTHETIC_SECRET);
        assertThat(provider.getApiKey()).isEqualTo(SYNTHETIC_SECRET);

        ArgumentCaptor<GetSecretValueRequest> request = ArgumentCaptor.forClass(GetSecretValueRequest.class);
        verify(client, times(1)).getSecretValue(request.capture());
        assertThat(request.getValue().secretId()).isEqualTo(SECRET_ID);
    }

    @Test
    void reloadsSecretAfterTheFiveMinuteCacheExpires() {
        SecretsManagerClient client = mock(SecretsManagerClient.class);
        when(client.getSecretValue(any(GetSecretValueRequest.class))).thenReturn(
                GetSecretValueResponse.builder().secretString("{\"API_KEY\":\"" + SYNTHETIC_SECRET + "\"}").build());
        MutableClock clock = new MutableClock(FIXED_CLOCK.instant());
        AwsTypeSafeApiKeyProvider provider = provider(client, clock);

        assertThat(provider.getApiKey()).isEqualTo(SYNTHETIC_SECRET);
        clock.advance(Duration.ofMinutes(5));
        assertThat(provider.getApiKey()).isEqualTo(SYNTHETIC_SECRET);

        ArgumentCaptor<GetSecretValueRequest> requests = ArgumentCaptor.forClass(GetSecretValueRequest.class);
        verify(client, times(2)).getSecretValue(requests.capture());
        assertThat(requests.getAllValues()).extracting(GetSecretValueRequest::secretId).containsOnly(SECRET_ID);
    }

    @Test
    void hidesAwsErrorsAndRejectsSecretsWithoutApiKey() {
        SecretsManagerClient client = mock(SecretsManagerClient.class);
        when(client.getSecretValue(any(GetSecretValueRequest.class)))
                .thenThrow(new IllegalStateException("synthetic-private-aws-error"));
        AwsTypeSafeApiKeyProvider provider = provider(client, FIXED_CLOCK);

        assertThatThrownBy(provider::getApiKey)
                .isInstanceOf(SearchInterpretationUnavailableException.class)
                .hasMessage("Natural-language interpretation is temporarily unavailable.")
                .hasNoCause();
    }

    @Test
    void rejectsSecretJsonWithoutTheExpectedField() {
        SecretsManagerClient client = mock(SecretsManagerClient.class);
        when(client.getSecretValue(any(GetSecretValueRequest.class))).thenReturn(
                GetSecretValueResponse.builder().secretString("{\"OTHER\":\"redacted\"}").build());
        AwsTypeSafeApiKeyProvider provider = provider(client, FIXED_CLOCK);

        assertThatThrownBy(provider::getApiKey)
                .isInstanceOf(SearchInterpretationUnavailableException.class)
                .hasNoCause();
    }

    private static AwsTypeSafeApiKeyProvider provider(SecretsManagerClient client, Clock clock) {
        return new AwsTypeSafeApiKeyProvider(
                client,
                JsonMapper.builder().build(),
                new TypeSafeSearchProperties(
                        "https://api.typesafe.ai", "jev-1.13.0", SECRET_ID, Duration.ofSeconds(3), 0.65),
                clock);
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }
    }
}
