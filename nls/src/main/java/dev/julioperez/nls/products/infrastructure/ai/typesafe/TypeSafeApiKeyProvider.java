package dev.julioperez.nls.products.infrastructure.ai.typesafe;

@FunctionalInterface
public interface TypeSafeApiKeyProvider {
    String getApiKey();
}
