package dev.julioperez.nls.evaluation.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.julioperez.nls.products.application.SearchInterpretationTelemetry;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EvaluationMetricsTest {
    @Test
    void reportsExactnessClarificationAndProviderMetricsByTag() {
        var provider = new SearchInterpretationTelemetry("typesafe", "jev-1.13.0", true, 100, 10, 25);
        var deterministic = new SearchInterpretationTelemetry(
                "deterministic_refinement", "not_called", false, null, null, 1);
        var rejected = new SearchInterpretationTelemetry(
                "rejected", "jev-1.13.0", true, 40, 8, 18, "LOW_CONFIDENCE", "intent");
        var expectedDiscovery = new EvaluationCorpus.ExpectedTurn(
                "RESULTS", List.of(), List.of("Remera básica de algodón"), List.of("NEGRO"),
                List.of("NEGRO|M|24990|12"), "START_SEARCH", false, null);
        var expectedSearch = new EvaluationCorpus.ExpectedTurn(
                "RESULTS", List.of("category|=|REMERAS"), List.of("Remera básica de algodón"),
                List.of("NEGRO"), List.of("NEGRO|M|24990|12"), "START_SEARCH", false, null);
        var actualSearch = new EvaluationRunWorker.ActualTurn(
                "RESULTS", List.of("category|=|REMERAS"), List.of("Remera básica de algodón"),
                List.of("NEGRO"), List.of("NEGRO|M|24990|12"), 1, List.of(), "Encontré una remera.",
                "START_SEARCH", false, provider);
        var searchTurn = new EvaluationRunWorker.TurnReport(1, "remeras negras", List.of("typo"), "MESSAGE",
                expectedSearch, actualSearch, true, List.of(), 25);

        var expectedClarification = new EvaluationCorpus.ExpectedTurn(
                "NEEDS_CLARIFICATION", List.of(), List.of(), List.of(), List.of(), "ASK_CLARIFICATION", true,
                "product_and_filter_unspecified");
        var actualClarification = new EvaluationRunWorker.ActualTurn(
                "NEEDS_CLARIFICATION", List.of(), List.of(), List.of(), List.of(), 0, List.of(),
                "¿Qué producto buscás?", "ASK_CLARIFICATION", true, deterministic);
        var clarificationTurn = new EvaluationRunWorker.TurnReport(1, "algo lindo", List.of("ambiguity"), "MESSAGE",
                expectedClarification, actualClarification, true, List.of(), 1);
        var rejectedActual = new EvaluationRunWorker.ActualTurn(
                "NEEDS_CLARIFICATION", List.of(), List.of(), List.of(), List.of(), 0, List.of(),
                "¿Qué buscás?", "ASK_CLARIFICATION", true, rejected);
        var rejectedTurn = new EvaluationRunWorker.TurnReport(1, "¿Qué vendes?", List.of("broad_discovery"),
                "MESSAGE", expectedDiscovery, rejectedActual, false, List.of("outcome", "grounding"), 18);

        var cases = List.of(
                new EvaluationRunWorker.CaseReport("typo-case", List.of("typo"), true, 25, List.of(searchTurn)),
                new EvaluationRunWorker.CaseReport(
                        "ambiguous-case", List.of("ambiguity"), true, 1, List.of(clarificationTurn)),
                new EvaluationRunWorker.CaseReport(
                        "discovery-case", List.of("broad_discovery"), false, 18, List.of(rejectedTurn)));

        EvaluationRunWorker.RunMetrics metrics = EvaluationRunWorker.metrics(cases);

        assertThat(metrics.caseCount()).isEqualTo(3);
        assertThat(metrics.passedCaseCount()).isEqualTo(2);
        assertThat(metrics.turnCount()).isEqualTo(3);
        assertThat(metrics.passedTurnCount()).isEqualTo(2);
        assertThat(metrics.outcomeAccuracy()).isEqualTo(2.0 / 3.0);
        assertThat(metrics.criteriaExactMatchRate()).isEqualTo(1.0);
        assertThat(metrics.contextActionAccuracy()).isEqualTo(2.0 / 3.0);
        assertThat(metrics.groundingExactMatchRate()).isEqualTo(2.0 / 3.0);
        assertThat(metrics.clarificationPrecision()).isEqualTo(0.5);
        assertThat(metrics.clarificationRecall()).isEqualTo(1.0);
        assertThat(metrics.providerCalls()).isEqualTo(2);
        assertThat(metrics.inputTokens()).isEqualTo(140);
        assertThat(metrics.outputTokens()).isEqualTo(18);
        assertThat(metrics.providerLatencyP50Millis()).isEqualTo(18);
        assertThat(metrics.providerLatencyP95Millis()).isEqualTo(25);
        assertThat(metrics.interpretationFailureReasons()).containsEntry("LOW_CONFIDENCE", 1L);
        assertThat(metrics.byCategory()).containsKeys("typo", "ambiguity", "broad_discovery");
        assertThat(metrics.byCategory().get("ambiguity").clarificationRecall()).isEqualTo(1.0);
    }
}
