package dev.julioperez.nls.evaluation.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "nls.evaluation")
public record EvaluationProperties(
        boolean enabled,
        String corpusLocation,
        String catalogFixtureLocation,
        int workerThreads,
        int queueCapacity,
        String allowedDatabaseHosts) {
    public EvaluationProperties {
        corpusLocation = corpusLocation == null || corpusLocation.isBlank()
                ? "classpath:evaluation/catalog-conversation-v1.1.1.json"
                : corpusLocation;
        catalogFixtureLocation = catalogFixtureLocation == null || catalogFixtureLocation.isBlank()
                ? "classpath:evaluation/catalog-conversation-seed-v1.1.0.sql"
                : catalogFixtureLocation;
        workerThreads = workerThreads < 1 ? 1 : workerThreads;
        queueCapacity = queueCapacity < 1 ? 2 : queueCapacity;
        allowedDatabaseHosts = allowedDatabaseHosts == null || allowedDatabaseHosts.isBlank()
                ? "localhost,127.0.0.1,::1"
                : allowedDatabaseHosts;
    }
}
