package dev.julioperez.nls.conversation.infrastructure.whatsapp;

import dev.julioperez.nls.infrastructure.logging.RequestLogContext;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(prefix = "nls.whatsapp", name = "enabled", havingValue = "true")
public final class MetaWhatsAppMessageSender implements WhatsAppMessageSender {
    private static final Logger log = LoggerFactory.getLogger(MetaWhatsAppMessageSender.class);
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(5);
    private static final int MAX_TEXT_LENGTH = 4096;

    private final RestClient restClient;
    private final WhatsAppProperties properties;
    private final WhatsAppSecretsProvider secretsProvider;
    private final ObjectMapper objectMapper;

    @Autowired
    public MetaWhatsAppMessageSender(
            WhatsAppProperties properties,
            WhatsAppSecretsProvider secretsProvider,
            ObjectMapper objectMapper) {
        this(buildRestClient(properties), properties, secretsProvider, objectMapper);
    }

    MetaWhatsAppMessageSender(
            RestClient restClient,
            WhatsAppProperties properties,
            WhatsAppSecretsProvider secretsProvider) {
        this(restClient, properties, secretsProvider, new ObjectMapper());
    }

    MetaWhatsAppMessageSender(
            RestClient restClient,
            WhatsAppProperties properties,
            WhatsAppSecretsProvider secretsProvider,
            ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.properties = properties;
        this.secretsProvider = secretsProvider;
        this.objectMapper = objectMapper;
    }

    private static RestClient buildRestClient(WhatsAppProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.graphApiBaseUrl())
                .requestFactory(requestFactory(properties))
                .build();
    }

    @Override
    public void sendText(String recipientId, String text) {
        String requestId = RequestLogContext.requestId();
        if (isBlank(recipientId) || isBlank(text) || isBlank(properties.phoneNumberId())
                || isBlank(properties.graphApiVersion()) || isBlank(properties.graphApiBaseUrl())) {
            log.error("WHATSAPP_OUTBOUND_FAILED requestId={} reason=configuration_unavailable", requestId);
            throw new WhatsAppConfigurationUnavailableException();
        }
        long startedAt = System.nanoTime();
        log.info("WHATSAPP_OUTBOUND_STARTED requestId={}", requestId);
        try {
            var response = restClient.post()
                    .uri("/{version}/{phoneNumberId}/messages", properties.graphApiVersion(), properties.phoneNumberId())
                    .headers(headers -> headers.setBearerAuth(secretsProvider.get().accessToken()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "messaging_product", "whatsapp",
                            "to", recipientId,
                            "type", "text",
                            "text", Map.of("body", limit(text))))
                    .retrieve()
                    .toBodilessEntity();
            log.info("WHATSAPP_OUTBOUND_ACCEPTED requestId={} httpStatus={} durationMs={}",
                    requestId, response.getStatusCode().value(), elapsedMillis(startedAt));
        } catch (WhatsAppConfigurationUnavailableException exception) {
            log.error("WHATSAPP_OUTBOUND_FAILED requestId={} reason=secrets_unavailable", requestId);
            throw exception;
        } catch (RestClientResponseException exception) {
            ProviderError providerError = providerError(exception);
            log.error("WHATSAPP_OUTBOUND_FAILED requestId={} httpStatus={} providerErrorCode={} "
                            + "providerErrorType={} durationMs={}",
                    requestId, exception.getStatusCode().value(), providerError.code(), providerError.type(),
                    elapsedMillis(startedAt));
            throw new WhatsAppDeliveryException();
        } catch (RestClientException exception) {
            // Do not propagate response bodies; they can contain provider or recipient data.
            log.error("WHATSAPP_OUTBOUND_FAILED requestId={} reason=transport_error errorType={} durationMs={}",
                    requestId, exception.getClass().getSimpleName(), elapsedMillis(startedAt));
            throw new WhatsAppDeliveryException();
        }
    }

    private ProviderError providerError(RestClientResponseException exception) {
        try {
            JsonNode error = objectMapper.readTree(exception.getResponseBodyAsByteArray()).path("error");
            return new ProviderError(safeProviderValue(error.path("code").asText("unknown")),
                    safeProviderValue(error.path("type").asText("unknown")));
        } catch (RuntimeException ignored) {
            return new ProviderError("unknown", "unknown");
        }
    }

    private static String safeProviderValue(String value) {
        return value != null && value.matches("[A-Za-z0-9_.-]{1,80}") ? value : "unknown";
    }

    private static long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private record ProviderError(String code, String type) {}

    private static JdkClientHttpRequestFactory requestFactory(WhatsAppProperties properties) {
        Duration connectTimeout = validOr(properties.connectTimeout(), DEFAULT_CONNECT_TIMEOUT);
        Duration readTimeout = validOr(properties.readTimeout(), DEFAULT_READ_TIMEOUT);
        HttpClient client = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(readTimeout);
        return factory;
    }

    private static Duration validOr(Duration value, Duration fallback) {
        return value == null || value.isNegative() || value.isZero() ? fallback : value;
    }

    private static String limit(String value) {
        return value.length() <= MAX_TEXT_LENGTH ? value : value.substring(0, MAX_TEXT_LENGTH);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
