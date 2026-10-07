package dev.julioperez.nls.infrastructure.logging;

import org.slf4j.MDC;

public final class RequestLogContext {
    private RequestLogContext() {}

    public static String requestId() {
        String requestId = MDC.get(RequestCorrelationIdFilter.MDC_KEY);
        return requestId == null || requestId.isBlank() ? "unavailable" : requestId;
    }
}
