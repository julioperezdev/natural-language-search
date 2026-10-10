package dev.julioperez.nls.products.infrastructure.ai.typesafe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.julioperez.nls.products.application.SearchConversationContext;
import dev.julioperez.nls.products.application.SearchSnapshotReferenceResolver;
import dev.julioperez.nls.products.domain.search.Criteria;
import dev.julioperez.nls.products.domain.search.Filter;
import dev.julioperez.nls.products.domain.search.FilterOperator;
import dev.julioperez.nls.products.domain.search.SearchFieldSchema;
import dev.julioperez.nls.products.domain.search.SearchFieldType;
import dev.julioperez.nls.products.domain.search.SearchPaginationSchema;
import dev.julioperez.nls.products.domain.search.SearchSchema;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Opt-in live evaluation. It never handles WhatsApp traffic or writes to the catalog database. */
class JevConversationContextEvaluationTest {
    private static final Logger log = LoggerFactory.getLogger(JevConversationContextEvaluationTest.class);
    private static final String FIXTURE = "/evaluation/jev-conversation-context.json";
    private static final int RECENT_MESSAGE_COUNT = 4;
    private static final int DEFAULT_RUN_COUNT = 3;

    @Test
    void reportsConfidenceCalibrationAndThresholdTradeoffs() {
        List<ConfidenceObservation> observations = List.of(
                new ConfidenceObservation(1, "case-1", "synthetic", "baseline", "jev-test",
                        "color", "BLACK", "BLACK", 0.8, Map.of("BLACK", 0.8, "WHITE", 0.2)),
                new ConfidenceObservation(1, "case-2", "synthetic", "baseline", "jev-test",
                        "color", "BLACK", "WHITE", 0.6, Map.of("BLACK", 0.4, "WHITE", 0.6)));

        assertThat(expectedCalibrationError(observations)).isCloseTo(0.4, offset(1.0e-10));
        assertThat(meanBrierScore(observations)).isCloseTo(0.2, offset(1.0e-10));
        assertThat(calibrationCsv(observations))
                .contains("\"field_summary\",\"color\"")
                .contains("0.4000")
                .contains("\"0.6500\",\"1\",\"0.5000\",\"1.0000\",\"0.0000\"")
                .contains("\"threshold\",\"color\"");
    }

    @Test
    void comparesBaselineRecentAndFullConversationContextWithJev() throws Exception {
        assumeTrue(Boolean.getBoolean("nls.jev.evaluation.enabled"),
                "Enable the live JEV evaluation explicitly with -Dnls.jev.evaluation.enabled=true.");

        JsonMapper mapper = JsonMapper.builder().build();
        TypeSafeSearchProperties properties = properties();
        Region region = Region.of(System.getProperty(
                "nls.jev.evaluation.region", System.getenv().getOrDefault("NLS_AWS_REGION", "us-east-1")));
        try (SecretsManagerClient secrets = SecretsManagerClient.builder().region(region).build()) {
            TypeSafeSearchDecisionEngine engine = new TypeSafeSearchDecisionEngine(
                    RestClient.builder().build(), properties,
                    new AwsTypeSafeApiKeyProvider(secrets, mapper, properties));
            List<EvaluationCase> cases = loadCases(mapper);
            assertThat(cases).anyMatch(evaluationCase -> evaluationCase.history().size() == 20);
            int runCount = runCount();
            List<EvaluationRow> rows = evaluate(engine, cases, runCount);
            List<ConfidenceObservation> confidenceObservations = confidenceObservations(rows);
            Path report = Path.of("target", "jev-conversation-context-evaluation.csv");
            Path decisionsReport = Path.of("target", "jev-confidence-decisions.csv");
            Path calibrationReport = Path.of("target", "jev-confidence-calibration.csv");
            Files.createDirectories(report.getParent());
            Files.writeString(report, csv(rows, mapper));
            Files.writeString(decisionsReport, confidenceCsv(confidenceObservations));
            Files.writeString(calibrationReport, calibrationCsv(confidenceObservations));

            log.info("JEV_CONTEXT_EVALUATION_REPORT path={} cases={} runs={} calls={} baselineExact={} snapshotsOnlyExact={} recentExact={} fullExact={} "
                            + "baselineFieldAccuracy={} snapshotsOnlyFieldAccuracy={} recentFieldAccuracy={} fullFieldAccuracy={} "
                            + "baselineProviderFieldAccuracy={} snapshotsOnlyProviderFieldAccuracy={} recentProviderFieldAccuracy={} fullProviderFieldAccuracy={} "
                            + "confidenceObservations={} confidenceUniqueCases={} confidenceEce={} confidenceBrier={} "
                            + "decisionsReport={} calibrationReport={}",
                    report.toAbsolutePath(), cases.size(), runCount, providerCallCount(rows), exactRate(rows, "baseline"),
                    exactRate(rows, "snapshots_only"), exactRate(rows, "recent_4"), exactRate(rows, "full_history"),
                    fieldAccuracy(rows, "baseline"), fieldAccuracy(rows, "snapshots_only"),
                    fieldAccuracy(rows, "recent_4"), fieldAccuracy(rows, "full_history"),
                    providerFieldAccuracy(rows, "baseline"),
                    providerFieldAccuracy(rows, "snapshots_only"),
                    providerFieldAccuracy(rows, "recent_4"), providerFieldAccuracy(rows, "full_history"),
                    confidenceObservations.size(), uniqueCaseCount(confidenceObservations),
                    formatOrUnknown(expectedCalibrationError(confidenceObservations)),
                    formatOrUnknown(meanBrierScore(confidenceObservations)),
                    decisionsReport.toAbsolutePath(), calibrationReport.toAbsolutePath());
            rows.forEach(row -> log.info(
                    "JEV_CONTEXT_EVALUATION case={} variant={} exact={} appliedFields={}/{} providerFields={}/{} method={} "
                            + "model={} choices={} inputTokens={} outputTokens={} durationMs={} actualCriteria={}",
                    row.caseId(), row.variant(), row.exactMatch(), row.matchedFields(), row.expectedFields(),
                    row.providerMatchedFields(), row.expectedFields(),
                    row.method(), row.providerModel(), row.providerChoices(), valueOrUnknown(row.inputTokens()),
                    valueOrUnknown(row.outputTokens()),
                    row.durationMillis(), row.actualCriteria()));

            int variantsPerRun = cases.stream()
                    .mapToInt(evaluationCase -> evaluationCase.searchSnapshots().isEmpty() ? 3 : 4)
                    .sum();
            assertThat(rows).hasSize(variantsPerRun * runCount);
            assertThat(rows).noneMatch(row -> row.method().startsWith("failed:"));
            if (cases.stream().anyMatch(evaluationCase -> !evaluationCase.searchSnapshots().isEmpty())) {
                List<EvaluationRow> firstSearchReferences = rows.stream()
                        .filter(row -> row.caseId().equals("refer-to-first-request-after-topic-change"))
                        .filter(row -> row.variant().equals("snapshots_only"))
                        .toList();
                assertThat(firstSearchReferences).hasSize(runCount)
                        .allMatch(row -> row.resolvedSearchReference().equals("SEARCH_1") && row.exactMatch());
            }
            assertThat(Files.exists(report)).isTrue();
            assertThat(Files.exists(decisionsReport)).isTrue();
            assertThat(Files.exists(calibrationReport)).isTrue();
        }
    }

    private static List<EvaluationRow> evaluate(
            TypeSafeSearchDecisionEngine engine,
            List<EvaluationCase> cases,
            int runCount) {
        List<EvaluationRow> rows = new ArrayList<>();
        for (int run = 1; run <= runCount; run++) {
            for (EvaluationCase evaluationCase : cases) {
                List<Variant> variants = List.of(
                        new Variant("baseline", SearchConversationContext.empty()),
                        new Variant("recent_4", context(
                                last(evaluationCase.history(), RECENT_MESSAGE_COUNT), evaluationCase.searchSnapshots())),
                        new Variant("full_history", context(
                                evaluationCase.history(), evaluationCase.searchSnapshots())));
                if (!evaluationCase.searchSnapshots().isEmpty()) {
                    variants = List.of(
                            new Variant("baseline", SearchConversationContext.empty()),
                            new Variant("snapshots_only", context(List.of(), evaluationCase.searchSnapshots())),
                            new Variant("recent_4", context(
                                    last(evaluationCase.history(), RECENT_MESSAGE_COUNT),
                                    evaluationCase.searchSnapshots())),
                            new Variant("full_history", context(
                                    evaluationCase.history(), evaluationCase.searchSnapshots())));
                }
                for (Variant variant : variants) {
                    Criteria interpretationCurrent = evaluationCase.currentCriteria();
                    SearchConversationContext interpretationContext = variant.context();
                    String resolvedReference = "none";
                    if (!interpretationContext.searchSnapshots().isEmpty()
                            && SearchSnapshotReferenceResolver.isExplicitReference(evaluationCase.message())) {
                        var resolved = SearchSnapshotReferenceResolver.resolve(
                                evaluationCase.message(), interpretationContext.searchSnapshots());
                        if (resolved.isPresent()) {
                            var snapshot = resolved.orElseThrow();
                            interpretationCurrent = snapshot.criteria();
                            List<SearchConversationContext.Turn> resolvedTurns = variant.name().equals("snapshots_only")
                                    ? List.of()
                                    : interpretationContext.turns();
                            interpretationContext = new SearchConversationContext(
                                    resolvedTurns, List.of(), snapshot.reference());
                            resolvedReference = snapshot.reference();
                        }
                    }
                    TypeSafeInterpretationEvaluation result;
                    try {
                        result = engine.interpretForEvaluation(
                                evaluationCase.message(), schema(), interpretationCurrent, interpretationContext);
                    } catch (RuntimeException exception) {
                        log.warn("JEV_CONTEXT_EVALUATION_CALL_FAILED case={} variant={} errorType={}",
                                evaluationCase.id(), variant.name(), exception.getClass().getSimpleName());
                        rows.add(new EvaluationRow(
                                run, evaluationCase.id(), evaluationCase.source(), variant.name(), resolvedReference,
                                false, 0,
                                evaluationCase.expectedCriteria().filters().size(),
                                0, "failed:" + exception.getClass().getSimpleName(), "unavailable", "unavailable",
                                List.of(), evaluationCase.expectedDecisions(),
                                null, null, -1, "ERROR"));
                        continue;
                    }
                    if (result.failureReason() != null) {
                        int providerMatched = providerMatchedFields(
                                result.providerChoices(), interpretationCurrent,
                                evaluationCase.expectedCriteria());
                        rows.add(new EvaluationRow(
                                run, evaluationCase.id(), evaluationCase.source(), variant.name(), resolvedReference,
                                false, 0,
                                evaluationCase.expectedCriteria().filters().size(),
                                providerMatched,
                                "rejected:" + result.failureReason().name() + ":"
                                        + fieldOrNone(result.failureField()),
                                result.providerChoices(), result.providerModel(), result.providerDecisions(),
                                evaluationCase.expectedDecisions(), result.inputTokens(), result.outputTokens(),
                                result.durationMillis(), "REJECTED"));
                        continue;
                    }
                    Match match = compare(result.criteria(), evaluationCase.expectedCriteria());
                    int providerMatched = providerMatchedFields(
                            result.providerChoices(), interpretationCurrent,
                            evaluationCase.expectedCriteria());
                    rows.add(new EvaluationRow(
                            run, evaluationCase.id(), evaluationCase.source(), variant.name(),
                            resolvedReference,
                            match.exact(), match.matchedFields(),
                            match.expectedFields(), providerMatched, result.method(), result.providerChoices(),
                            result.providerModel(), result.providerDecisions(), evaluationCase.expectedDecisions(),
                            result.inputTokens(), result.outputTokens(), result.durationMillis(),
                            describe(result.criteria())));
                }
            }
        }
        return List.copyOf(rows);
    }

    private static List<EvaluationCase> loadCases(JsonMapper mapper) throws Exception {
        try (InputStream input = JevConversationContextEvaluationTest.class.getResourceAsStream(FIXTURE)) {
            if (input == null) {
                throw new IllegalStateException("JEV context evaluation fixture is missing.");
            }
            JsonNode root = mapper.readTree(input);
            List<EvaluationCase> cases = new ArrayList<>();
            for (JsonNode node : root) {
                List<SearchConversationContext.Turn> history = new ArrayList<>();
                for (JsonNode turn : node.path("history")) {
                    SearchConversationContext.Role role = SearchConversationContext.Role.valueOf(
                            turn.path("role").stringValue().toUpperCase(Locale.ROOT));
                    history.add(new SearchConversationContext.Turn(role, turn.path("content").stringValue()));
                }
                List<SearchConversationContext.SearchSnapshot> searchSnapshots = new ArrayList<>();
                for (JsonNode snapshot : node.path("searchSnapshots")) {
                    searchSnapshots.add(new SearchConversationContext.SearchSnapshot(
                            snapshot.path("sequence").longValue(),
                            criteria(snapshot.path("criteria")),
                            snapshot.path("totalResults").longValue()));
                }
                Map<String, String> expectedDecisions = new LinkedHashMap<>();
                node.path("expectedDecisions").properties().forEach(entry ->
                        expectedDecisions.put(entry.getKey(), entry.getValue().stringValue()));
                cases.add(new EvaluationCase(
                        node.path("id").stringValue(),
                        node.path("source").isTextual() ? node.path("source").stringValue() : "synthetic",
                        List.copyOf(history), List.copyOf(searchSnapshots), node.path("message").stringValue(),
                        criteria(node.path("currentCriteria")), criteria(node.path("expectedCriteria")),
                        Map.copyOf(expectedDecisions)));
            }
            return List.copyOf(cases);
        }
    }

    private static Criteria criteria(JsonNode filters) {
        List<Filter> parsed = new ArrayList<>();
        for (JsonNode filter : filters) {
            parsed.add(new Filter(
                    filter.path("field").stringValue(),
                    FilterOperator.fromValue(filter.path("operator").stringValue()),
                    value(filter.path("value"))));
        }
        return new Criteria(parsed, null, 10, 0);
    }

    private static Object value(JsonNode value) {
        if (value.isTextual()) {
            return value.stringValue();
        }
        if (value.isNumber()) {
            return value.decimalValue();
        }
        throw new IllegalArgumentException("Evaluation criteria values must be strings or numbers.");
    }

    private static SearchConversationContext context(
            List<SearchConversationContext.Turn> turns,
            List<SearchConversationContext.SearchSnapshot> snapshots) {
        return new SearchConversationContext(turns, snapshots);
    }

    private static List<SearchConversationContext.Turn> last(
            List<SearchConversationContext.Turn> turns,
            int maximum) {
        return turns.subList(Math.max(0, turns.size() - maximum), turns.size());
    }

    private static Match compare(Criteria actual, Criteria expected) {
        Map<String, Filter> actualByField = index(actual);
        Map<String, Filter> expectedByField = index(expected);
        int matched = 0;
        for (Map.Entry<String, Filter> entry : expectedByField.entrySet()) {
            Filter found = actualByField.get(entry.getKey());
            if (found != null && sameFilter(found, entry.getValue())) {
                matched++;
            }
        }
        return new Match(matched == expectedByField.size() && actualByField.size() == expectedByField.size(),
                matched, expectedByField.size());
    }

    private static Map<String, Filter> index(Criteria criteria) {
        return criteria.filters().stream().collect(Collectors.toMap(
                Filter::field, filter -> filter, (first, ignored) -> first, LinkedHashMap::new));
    }

    private static boolean sameFilter(Filter actual, Filter expected) {
        if (actual.operator() != expected.operator()) {
            return false;
        }
        if (actual.value() instanceof Number actualNumber && expected.value() instanceof Number expectedNumber) {
            return new BigDecimal(actualNumber.toString()).compareTo(new BigDecimal(expectedNumber.toString())) == 0;
        }
        return Objects.equals(actual.value(), expected.value());
    }

    private static String describe(Criteria criteria) {
        return criteria.filters().stream()
                .map(filter -> filter.field() + filter.operator().value() + filter.value())
                .sorted()
                .collect(Collectors.joining(";"));
    }

    private static double exactRate(List<EvaluationRow> rows, String variant) {
        List<EvaluationRow> selected = rows.stream().filter(row -> row.variant().equals(variant)).toList();
        return selected.isEmpty() ? 0.0 : (double) selected.stream().filter(EvaluationRow::exactMatch).count()
                / selected.size();
    }

    private static String fieldAccuracy(List<EvaluationRow> rows, String variant) {
        List<EvaluationRow> selected = rows.stream().filter(row -> row.variant().equals(variant)).toList();
        int expected = selected.stream().mapToInt(EvaluationRow::expectedFields).sum();
        int matched = selected.stream().mapToInt(EvaluationRow::matchedFields).sum();
        return expected == 0 ? "n/a" : matched + "/" + expected;
    }

    private static String providerFieldAccuracy(List<EvaluationRow> rows, String variant) {
        List<EvaluationRow> selected = rows.stream().filter(row -> row.variant().equals(variant)).toList();
        int expected = selected.stream().mapToInt(EvaluationRow::expectedFields).sum();
        int matched = selected.stream().mapToInt(EvaluationRow::providerMatchedFields).sum();
        return expected == 0 ? "n/a" : matched + "/" + expected;
    }

    private static int providerMatchedFields(String providerChoices, Criteria current, Criteria expected) {
        Map<String, String> choices = new LinkedHashMap<>();
        if (providerChoices != null && !providerChoices.equals("not_called")
                && !providerChoices.equals("unavailable")) {
            for (String entry : providerChoices.split(";")) {
                int separator = entry.indexOf('=');
                int confidence = entry.lastIndexOf('@');
                if (separator > 0 && confidence > separator) {
                    choices.put(entry.substring(0, separator), entry.substring(separator + 1, confidence));
                }
            }
        }
        Map<String, Filter> currentByField = index(current);
        int matched = 0;
        for (Filter expectedFilter : expected.filters()) {
            String selected = choices.get(expectedFilter.field());
            Filter currentFilter = currentByField.get(expectedFilter.field());
            if (selected == null) {
                if (currentFilter != null && sameFilter(currentFilter, expectedFilter)) {
                    matched++;
                }
            } else if (selected.equals("__KEEP__")) {
                if (currentFilter != null && sameFilter(currentFilter, expectedFilter)) {
                    matched++;
                }
            } else if (selected.equals(String.valueOf(expectedFilter.value()))) {
                matched++;
            }
        }
        return matched;
    }

    private static String csv(List<EvaluationRow> rows, JsonMapper mapper) throws Exception {
        StringBuilder report = new StringBuilder(
                "run,case_id,source,variant,resolved_search_reference,exact_match,applied_fields,expected_fields,provider_fields,method,provider_model,provider_choices,provider_decisions,expected_decisions,input_tokens,output_tokens,duration_ms,actual_criteria\n");
        for (EvaluationRow row : rows) {
            report.append(row.run()).append(',').append(row.caseId()).append(',').append(row.source())
                    .append(',').append(row.variant())
                    .append(',').append(row.resolvedSearchReference())
                    .append(',').append(row.exactMatch())
                    .append(',').append(row.matchedFields()).append(',').append(row.expectedFields()).append(',')
                    .append(row.providerMatchedFields()).append(',')
                    .append(row.method()).append(',').append(row.providerModel()).append(',')
                    .append(quoted(row.providerChoices())).append(',')
                    .append(quoted(mapper.writeValueAsString(row.providerDecisions()))).append(',')
                    .append(quoted(mapper.writeValueAsString(row.expectedDecisions()))).append(',')
                    .append(valueOrUnknown(row.inputTokens())).append(',')
                    .append(valueOrUnknown(row.outputTokens())).append(',').append(row.durationMillis()).append(',')
                    .append(quoted(row.actualCriteria())).append('\n');
        }
        return report.toString();
    }

    private static List<ConfidenceObservation> confidenceObservations(List<EvaluationRow> rows) {
        List<ConfidenceObservation> observations = new ArrayList<>();
        for (EvaluationRow row : rows) {
            for (TypeSafeChoiceEvaluation decision : row.providerDecisions()) {
                String expected = row.expectedDecisions().get(decision.field());
                if (expected == null || decision.choice() == null || decision.confidence() == null
                        || decision.confidence() < 0.0 || decision.confidence() > 1.0
                        || !decision.probabilities().containsKey(expected)
                        || !validProbabilityDistribution(decision.probabilities())) {
                    continue;
                }
                observations.add(new ConfidenceObservation(
                        row.run(), row.caseId(), row.source(), row.variant(), row.providerModel(),
                        decision.field(), decision.choice(), expected,
                        decision.confidence(), decision.probabilities()));
            }
        }
        return List.copyOf(observations);
    }

    private static String confidenceCsv(List<ConfidenceObservation> observations) throws Exception {
        JsonMapper mapper = JsonMapper.builder().build();
        StringBuilder report = new StringBuilder(
                "run,case_id,source,variant,model,field,predicted_choice,expected_choice,correct,confidence,brier_score,probabilities\n");
        for (ConfidenceObservation observation : observations) {
            report.append(observation.run()).append(',').append(observation.caseId()).append(',')
                    .append(observation.source()).append(',').append(observation.variant()).append(',')
                    .append(observation.model()).append(',').append(observation.field()).append(',')
                    .append(quoted(observation.predictedChoice())).append(',')
                    .append(quoted(observation.expectedChoice())).append(',')
                    .append(observation.correct()).append(',').append(format(observation.confidence())).append(',')
                    .append(format(observation.brierScore())).append(',')
                    .append(quoted(mapper.writeValueAsString(observation.probabilities()))).append('\n');
        }
        return report.toString();
    }

    private static String calibrationCsv(List<ConfidenceObservation> observations) {
        StringBuilder report = new StringBuilder(
                "row_type,field,confidence_lower,confidence_upper,sample_count,unique_cases,mean_confidence,empirical_accuracy,calibration_gap,mean_brier_score,ece,threshold,accepted_count,coverage,accepted_accuracy,accepted_error_rate\n");
        if (observations.isEmpty()) {
            return report.toString();
        }
        Map<String, List<ConfidenceObservation>> byField = observations.stream().collect(Collectors.groupingBy(
                ConfidenceObservation::field, TreeMap::new, Collectors.toList()));
        byField.forEach((field, fieldObservations) -> appendFieldCalibration(report, field, fieldObservations));
        appendFieldCalibration(report, "ALL", observations);
        return report.toString();
    }

    private static void appendFieldCalibration(
            StringBuilder report,
            String field,
            List<ConfidenceObservation> observations) {
        Map<Integer, List<ConfidenceObservation>> byBin = observations.stream().collect(Collectors.groupingBy(
                observation -> Math.min(9, (int) (observation.confidence() * 10)),
                TreeMap::new, Collectors.toList()));
        double ece = 0.0;
        for (Map.Entry<Integer, List<ConfidenceObservation>> entry : byBin.entrySet()) {
            int bin = entry.getKey();
            List<ConfidenceObservation> values = entry.getValue();
            double meanConfidence = meanConfidence(values);
            double accuracy = accuracy(values);
            double gap = Math.abs(meanConfidence - accuracy);
            ece += (double) values.size() / observations.size() * gap;
            appendCsvRow(report, "confidence_bin", field,
                    format(bin / 10.0), format((bin + 1) / 10.0), Integer.toString(values.size()),
                    Integer.toString(uniqueCaseCount(values)), format(meanConfidence), format(accuracy),
                    format(gap), format(meanBrierScore(values)), "", "", "", "", "", "");
        }
        appendCsvRow(report, "field_summary", field, "", "", Integer.toString(observations.size()),
                Integer.toString(uniqueCaseCount(observations)), format(meanConfidence(observations)),
                format(accuracy(observations)), "", format(meanBrierScore(observations)), format(ece),
                "", "", "", "", "");

        for (double threshold : List.of(0.0, 0.50, 0.55, 0.60, 0.65, 0.70, 0.75, 0.80, 0.85, 0.90, 0.95, 1.0)) {
            List<ConfidenceObservation> accepted = observations.stream()
                    .filter(observation -> observation.confidence() >= threshold)
                    .toList();
            double coverage = (double) accepted.size() / observations.size();
            String acceptedAccuracy = accepted.isEmpty() ? "" : format(accuracy(accepted));
            String acceptedErrorRate = accepted.isEmpty() ? "" : format(1.0 - accuracy(accepted));
            appendCsvRow(report, "threshold", field, "", "", Integer.toString(observations.size()),
                    Integer.toString(uniqueCaseCount(observations)), "", "", "", "", "",
                    format(threshold), Integer.toString(accepted.size()), format(coverage),
                    acceptedAccuracy, acceptedErrorRate);
        }
    }

    private static void appendCsvRow(StringBuilder report, String... cells) {
        for (int index = 0; index < cells.length; index++) {
            if (index > 0) {
                report.append(',');
            }
            report.append(quoted(cells[index]));
        }
        report.append('\n');
    }

    private static double expectedCalibrationError(List<ConfidenceObservation> observations) {
        if (observations.isEmpty()) {
            return Double.NaN;
        }
        Map<Integer, List<ConfidenceObservation>> byBin = observations.stream().collect(Collectors.groupingBy(
                observation -> Math.min(9, (int) (observation.confidence() * 10)), Collectors.toList()));
        return byBin.values().stream()
                .mapToDouble(values -> (double) values.size() / observations.size()
                        * Math.abs(meanConfidence(values) - accuracy(values)))
                .sum();
    }

    private static double meanBrierScore(List<ConfidenceObservation> observations) {
        return observations.stream().mapToDouble(ConfidenceObservation::brierScore).average().orElse(Double.NaN);
    }

    private static double meanConfidence(List<ConfidenceObservation> observations) {
        return observations.stream().mapToDouble(ConfidenceObservation::confidence).average().orElse(Double.NaN);
    }

    private static double accuracy(List<ConfidenceObservation> observations) {
        return observations.stream().filter(ConfidenceObservation::correct).count() / (double) observations.size();
    }

    private static int uniqueCaseCount(List<ConfidenceObservation> observations) {
        return (int) observations.stream().map(ConfidenceObservation::caseId).distinct().count();
    }

    private static boolean validProbabilityDistribution(Map<String, Double> probabilities) {
        double sum = probabilities.values().stream().mapToDouble(Double::doubleValue).sum();
        return !probabilities.isEmpty()
                && probabilities.values().stream().allMatch(value -> value >= 0.0 && value <= 1.0)
                && Math.abs(sum - 1.0) <= 0.001;
    }

    private static long providerCallCount(List<EvaluationRow> rows) {
        return rows.stream().filter(row -> !row.providerDecisions().isEmpty()).count();
    }

    private static String formatOrUnknown(double value) {
        return Double.isFinite(value) ? format(value) : "n/a";
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private static String quoted(String value) {
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    private static String valueOrUnknown(Integer value) {
        return value == null ? "unknown" : value.toString();
    }

    private static String fieldOrNone(String value) {
        return value == null ? "none" : value;
    }

    private static int runCount() {
        String raw = System.getProperty("nls.jev.evaluation.runs", Integer.toString(DEFAULT_RUN_COUNT));
        try {
            int count = Integer.parseInt(raw);
            if (count < 1 || count > 5) {
                throw new IllegalArgumentException("JEV evaluation runs must be between 1 and 5.");
            }
            return count;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("JEV evaluation runs must be an integer between 1 and 5.", exception);
        }
    }

    private static TypeSafeSearchProperties properties() {
        return new TypeSafeSearchProperties(
                System.getProperty("nls.jev.evaluation.endpoint", "https://api.typesafe.ai"),
                System.getProperty("nls.jev.evaluation.model", "jev-1.13.0"),
                System.getProperty("nls.jev.evaluation.secret-id", "wcs/prod/typesafe"),
                Duration.ofSeconds(20), 0.65);
    }

    private static SearchSchema schema() {
        return new SearchSchema("product-search", List.of(
                field("category", SearchFieldType.ENUM, List.of("=", "IN"), List.of("REMERAS", "BUZOS")),
                field("color", SearchFieldType.ENUM, List.of("=", "IN"), List.of("BLACK", "WHITE")),
                field("price", SearchFieldType.NUMBER, List.of("=", "<", "<=", ">", ">="), List.of()),
                field("productName", SearchFieldType.STRING, List.of("=", "CONTAINS"), List.of()),
                field("size", SearchFieldType.ENUM, List.of("=", "IN"), List.of("M", "L")),
                field("stock", SearchFieldType.INTEGER, List.of("=", ">", ">=", "<", "<="), List.of())),
                new SearchPaginationSchema(10, 50));
    }

    private static SearchFieldSchema field(
            String name,
            SearchFieldType type,
            List<String> operators,
            List<String> values) {
        return new SearchFieldSchema(name, name, type, operators, values, true, false);
    }

    private record EvaluationCase(
            String id,
            String source,
            List<SearchConversationContext.Turn> history,
            List<SearchConversationContext.SearchSnapshot> searchSnapshots,
            String message,
            Criteria currentCriteria,
            Criteria expectedCriteria,
            Map<String, String> expectedDecisions) {
    }

    private record Variant(String name, SearchConversationContext context) {
    }

    private record EvaluationRow(
            int run,
            String caseId,
            String source,
            String variant,
            String resolvedSearchReference,
            boolean exactMatch,
            int matchedFields,
            int expectedFields,
            int providerMatchedFields,
            String method,
            String providerChoices,
            String providerModel,
            List<TypeSafeChoiceEvaluation> providerDecisions,
            Map<String, String> expectedDecisions,
            Integer inputTokens,
            Integer outputTokens,
            long durationMillis,
            String actualCriteria) {
    }

    private record Match(boolean exact, int matchedFields, int expectedFields) {
    }

    private record ConfidenceObservation(
            int run,
            String caseId,
            String source,
            String variant,
            String model,
            String field,
            String predictedChoice,
            String expectedChoice,
            double confidence,
            Map<String, Double> probabilities) {

        private boolean correct() {
            return predictedChoice.equals(expectedChoice);
        }

        private double brierScore() {
            return probabilities.entrySet().stream()
                    .mapToDouble(entry -> {
                        double target = entry.getKey().equals(expectedChoice) ? 1.0 : 0.0;
                        double difference = entry.getValue() - target;
                        return difference * difference;
                    })
                    .sum();
        }
    }
}
