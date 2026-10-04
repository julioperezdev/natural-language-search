package dev.julioperez.nls.products.infrastructure.repository.postgres;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "nls.database")
public record NlsDatabaseProperties(String secretId, String schema) {
    public NlsDatabaseProperties {
        schema = schema == null || schema.isBlank() ? "public" : schema.trim();
        if (!schema.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("Database schema name is invalid.");
        }
    }
}
