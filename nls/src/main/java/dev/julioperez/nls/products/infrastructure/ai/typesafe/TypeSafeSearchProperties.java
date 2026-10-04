package dev.julioperez.nls.products.infrastructure.ai.typesafe;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "nls.search.interpretation.typesafe")
public record TypeSafeSearchProperties(
        String endpoint,
        String model,
        String secretId,
        Duration requestTimeout,
        double minimumConfidence) {

    public TypeSafeSearchProperties {
        endpoint = endpoint == null || endpoint.isBlank()
                ? "https://api.typesafe.ai"
                : endpoint.trim().replaceAll("/+$", "");
        if (!endpoint.startsWith("https://")) {
            throw new IllegalArgumentException("TypeSafe endpoint must use HTTPS.");
        }
        model = model == null || model.isBlank() ? "jev-1.13.0" : model.trim();
        secretId = secretId == null || secretId.isBlank() ? "wcs/prod/typesafe" : secretId.trim();
        requestTimeout = requestTimeout == null || requestTimeout.isZero() || requestTimeout.isNegative()
                ? Duration.ofSeconds(5)
                : requestTimeout.compareTo(Duration.ofSeconds(30)) > 0
                        ? Duration.ofSeconds(30)
                        : requestTimeout;
        if (!Double.isFinite(minimumConfidence) || minimumConfidence < 0.0 || minimumConfidence > 1.0) {
            minimumConfidence = 0.65;
        }
    }

    @Override
    public String toString() {
        return "TypeSafeSearchProperties[endpoint=" + endpoint
                + ", model=" + model
                + ", secretId=" + secretId
                + ", requestTimeout=" + requestTimeout
                + ", minimumConfidence=" + minimumConfidence + "]";
    }
}
