package dev.julioperez.nls.products.infrastructure.http;

import java.time.Instant;

public record SearchErrorResponse(String code, String message, Instant timestamp, String requestId) {
}
