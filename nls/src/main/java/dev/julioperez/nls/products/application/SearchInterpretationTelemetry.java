package dev.julioperez.nls.products.application;

public record SearchInterpretationTelemetry(
        String method,
        String providerModel,
        boolean providerCalled,
        Integer inputTokens,
        Integer outputTokens,
        long durationMillis,
        String failureReason,
        String failureField) {
    public SearchInterpretationTelemetry(
            String method,
            String providerModel,
            boolean providerCalled,
            Integer inputTokens,
            Integer outputTokens,
            long durationMillis) {
        this(method, providerModel, providerCalled, inputTokens, outputTokens, durationMillis, null, null);
    }
}
