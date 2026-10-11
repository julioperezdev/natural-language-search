package dev.julioperez.nls.evaluation.application;

import dev.julioperez.nls.conversation.application.ConversationMessageCommand;
import dev.julioperez.nls.conversation.application.ConversationMessageOutcome;
import dev.julioperez.nls.conversation.application.ConversationMessageResult;
import dev.julioperez.nls.conversation.application.ConversationMessageService;
import dev.julioperez.nls.conversation.domain.ConversationChannel;
import dev.julioperez.nls.conversation.domain.ConversationIdentity;
import dev.julioperez.nls.conversation.domain.ConversationRepository;
import dev.julioperez.nls.evaluation.infrastructure.repository.EvaluationRunJpaEntity;
import dev.julioperez.nls.evaluation.infrastructure.repository.EvaluationRunJpaRepository;
import dev.julioperez.nls.products.application.SearchInterpretationTelemetry;
import dev.julioperez.nls.products.domain.search.Filter;
import dev.julioperez.nls.products.domain.search.ProductSearchPage;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
@ConditionalOnProperty(prefix = "nls.evaluation", name = "enabled", havingValue = "true")
@Profile({"local", "test"})
public class EvaluationRunWorker {
    private static final Logger log = LoggerFactory.getLogger(EvaluationRunWorker.class);
    private final EvaluationRunJpaRepository runs;
    private final ConversationMessageService conversations;
    private final ConversationRepository conversationRepository;
    private final JsonMapper mapper;

    public EvaluationRunWorker(
            EvaluationRunJpaRepository runs,
            ConversationMessageService conversations,
            ConversationRepository conversationRepository,
            JsonMapper mapper) {
        this.runs = runs;
        this.conversations = conversations;
        this.conversationRepository = conversationRepository;
        this.mapper = mapper;
    }

    public void execute(UUID runId, EvaluationCorpus corpus) {
        var run = runs.findById(runId).orElseThrow();
        run.markRunning(Instant.now());
        runs.saveAndFlush(run);
        long startedAt = System.nanoTime();
        List<CaseReport> caseReports = new ArrayList<>();
        try {
            for (EvaluationCorpus.EvaluationCase evaluationCase : corpus.cases()) {
                caseReports.add(evaluateCase(runId, evaluationCase));
            }
            int passed = (int) caseReports.stream().filter(CaseReport::passed).count();
            RunMetrics metrics = metrics(caseReports);
            EvaluationReport report = new EvaluationReport(
                    corpus.suiteId(), corpus.version(), corpus.catalogVersion(), run.getCorpusSha256(),
                    run.getCatalogSha256(), run.getApplicationRevision(), runId, Instant.now(),
                    elapsedMillis(startedAt), metrics, caseReports);
            run.complete(caseReports.size(), passed, caseReports.size() - passed,
                    mapper.writeValueAsString(report), Instant.now());
            runs.saveAndFlush(run);
            log.info("EVALUATION_RUN_COMPLETED runId={} suite={} version={} cases={} passed={} failed={} "
                            + "providerCalls={} inputTokens={} outputTokens={} durationMs={}",
                    runId, corpus.suiteId(), corpus.version(), caseReports.size(), passed,
                    caseReports.size() - passed, metrics.providerCalls(), metrics.inputTokens(),
                    metrics.outputTokens(), report.durationMillis());
        } catch (RuntimeException exception) {
            run.fail(exception.getClass().getSimpleName(), Instant.now());
            runs.saveAndFlush(run);
            log.error("EVALUATION_RUN_FAILED runId={} errorType={}", runId, exception.getClass().getSimpleName());
        }
    }

    private CaseReport evaluateCase(UUID runId, EvaluationCorpus.EvaluationCase evaluationCase) {
        long startedAt = System.nanoTime();
        String participantId = "run-" + runId + "-" + evaluationCase.id();
        ConversationIdentity identity = new ConversationIdentity(
                ConversationChannel.API, "nls-evaluation", participantId);
        List<TurnReport> turnReports = new ArrayList<>();
        List<String> previousCriteria = List.of();
        List<List<String>> savedSearches = new ArrayList<>();
        for (int index = 0; index < evaluationCase.turns().size(); index++) {
            EvaluationCorpus.EvaluationTurn turn = evaluationCase.turns().get(index);
            long turnStartedAt = System.nanoTime();
            try {
                String providerMessageId = "evaluation-" + runId + "-" + evaluationCase.id() + "-" + (index + 1);
                ConversationMessageCommand command = new ConversationMessageCommand(identity, providerMessageId,
                        turn.message());
                ConversationMessageResult result = "RESET_CONTEXT".equals(turn.operation())
                        ? conversations.resetContext(command)
                        : conversations.handle(command);
                TurnReport turnReport = compare(index + 1, evaluationCase.tags(), turn, result, previousCriteria, savedSearches,
                        elapsedMillis(turnStartedAt));
                turnReports.add(turnReport);
                if (result.outcome() == ConversationMessageOutcome.CONTEXT_RESET) {
                    previousCriteria = List.of();
                    savedSearches.clear();
                } else {
                    previousCriteria = turnReport.actual().criteria();
                    if (result.results() != null) {
                        savedSearches.add(previousCriteria);
                    }
                }
            } catch (RuntimeException exception) {
                turnReports.add(new TurnReport(index + 1, turn.message(), evaluationCase.tags(), turn.operation(),
                        turn.expected(), null, false,
                        List.of("execution:" + exception.getClass().getSimpleName()), elapsedMillis(turnStartedAt)));
                log.warn("EVALUATION_TURN_FAILED runId={} caseId={} turn={} errorType={}",
                        runId, evaluationCase.id(), index + 1, exception.getClass().getSimpleName());
            }
        }
        try {
            conversationRepository.deleteByIdentity(identity);
        } catch (RuntimeException exception) {
            log.warn("EVALUATION_CONVERSATION_CLEANUP_FAILED runId={} caseId={} errorType={}",
                    runId, evaluationCase.id(), exception.getClass().getSimpleName());
        }
        return new CaseReport(evaluationCase.id(), evaluationCase.tags(),
                turnReports.stream().allMatch(TurnReport::passed), elapsedMillis(startedAt), turnReports);
    }

    private TurnReport compare(
            int turnNumber,
            List<String> tags,
            EvaluationCorpus.EvaluationTurn turn,
            ConversationMessageResult result,
            List<String> previousCriteria,
            List<List<String>> savedSearches,
            long durationMillis) {
        String action = "RESET_CONTEXT".equals(turn.operation())
                ? "RESET_CONTEXT"
                : contextAction(result.outcome(), canonicalCriteria(result.criteria().filters()),
                        previousCriteria, savedSearches);
        ActualTurn actual = actual(result, action);
        List<String> mismatches = new ArrayList<>();
        EvaluationCorpus.ExpectedTurn expected = turn.expected();
        if (!expected.outcome().equals(actual.outcome())) mismatches.add("outcome");
        if (!sorted(expected.criteria()).equals(sorted(actual.criteria()))) mismatches.add("criteria");
        if (!sorted(expected.productNames()).equals(sorted(actual.productNames()))) mismatches.add("productNames");
        if (!sorted(expected.variantColors()).equals(sorted(actual.variantColors()))) mismatches.add("variantColors");
        if (!sorted(expected.variants()).equals(sorted(actual.variants()))) mismatches.add("variants");
        if (expected.contextAction() != null && !expected.contextAction().equals(actual.contextAction())) {
            mismatches.add("contextAction");
        }
        if (expected.clarificationExpected() != null
                && expected.clarificationExpected() != actual.clarification()) mismatches.add("clarificationExpected");
        return new TurnReport(turnNumber, turn.message(), tags, turn.operation(), expected, actual,
                mismatches.isEmpty(), mismatches, durationMillis);
    }

    private static ActualTurn actual(ConversationMessageResult result, String contextAction) {
        ProductSearchPage page = result.results();
        List<String> productNames = page == null
                ? List.of()
                : page.items().stream().map(item -> item.name()).sorted().toList();
        List<String> variantColors = page == null
                ? List.of()
                : page.items().stream().flatMap(item -> item.variants().stream())
                        .map(variant -> variant.color()).distinct().sorted().toList();
        List<String> criteria = canonicalCriteria(result.criteria().filters());
        List<ProductFact> products = page == null ? List.of() : page.items().stream()
                .map(item -> new ProductFact(item.id(), item.name(), item.category(), item.variants().stream()
                        .map(variant -> new VariantFact(variant.id(), variant.color(), variant.size(),
                                variant.price(), variant.stock()))
                        .toList()))
                .toList();
        List<String> variants = products.stream().flatMap(product -> product.variants().stream())
                .map(EvaluationRunWorker::canonicalVariant).sorted().toList();
        return new ActualTurn(result.outcome().name(), criteria, productNames, variantColors, variants,
                page == null ? 0 : page.total(), products, result.reply(), contextAction,
                result.outcome() == ConversationMessageOutcome.NEEDS_CLARIFICATION,
                result.interpretationTelemetry());
    }

    private static List<String> canonicalCriteria(List<Filter> filters) {
        return filters.stream().map(EvaluationRunWorker::canonicalFilter).sorted().toList();
    }

    private static String canonicalFilter(Filter filter) {
        return filter.field() + "|" + filter.operator().value() + "|" + scalar(filter.value());
    }

    private static String canonicalVariant(VariantFact variant) {
        return variant.color() + "|" + variant.size() + "|" + scalar(variant.price()) + "|" + variant.stock();
    }

    private static String scalar(Object value) {
        if (value instanceof BigDecimal number) return number.stripTrailingZeros().toPlainString();
        return String.valueOf(value);
    }

    private static String contextAction(
            ConversationMessageOutcome outcome,
            List<String> current,
            List<String> previous,
            List<List<String>> savedSearches) {
        if (outcome == ConversationMessageOutcome.CONTEXT_RESET) return "RESET_CONTEXT";
        if (outcome == ConversationMessageOutcome.NEEDS_CLARIFICATION) return "ASK_CLARIFICATION";
        if (previous.isEmpty()) return "START_SEARCH";
        if (current.equals(sorted(previous))) return "KEEP_FILTERS";
        if (savedSearches.stream().limit(Math.max(0, savedSearches.size() - 1))
                .anyMatch(snapshot -> sorted(snapshot).equals(current))) return "RESTORE_SEARCH";

        Map<String, String> oldByField = byField(previous);
        Map<String, String> newByField = byField(current);
        boolean retainedPrevious = oldByField.entrySet().stream()
                .allMatch(entry -> entry.getValue().equals(newByField.get(entry.getKey())));
        if (retainedPrevious && newByField.size() > oldByField.size()) return "ADD_FILTER";
        boolean removed = oldByField.keySet().stream().anyMatch(field -> !newByField.containsKey(field));
        boolean replaced = oldByField.entrySet().stream()
                .anyMatch(entry -> newByField.containsKey(entry.getKey())
                        && !entry.getValue().equals(newByField.get(entry.getKey())));
        if (removed && !replaced) return "REMOVE_FILTER";
        if (replaced && !removed && oldByField.keySet().equals(newByField.keySet())) return "REPLACE_FILTER";
        return "UPDATE_FILTERS";
    }

    private static Map<String, String> byField(List<String> criteria) {
        Map<String, String> result = new LinkedHashMap<>();
        criteria.forEach(filter -> {
            int separator = filter.indexOf('|');
            if (separator > 0) result.put(filter.substring(0, separator), filter);
        });
        return result;
    }

    private static List<String> sorted(List<String> values) {
        return values.stream().sorted(Comparator.naturalOrder()).toList();
    }

    private static long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    static RunMetrics metrics(List<CaseReport> cases) {
        List<TurnReport> turns = cases.stream().flatMap(evaluationCase -> evaluationCase.turns().stream()).toList();
        List<SearchInterpretationTelemetry> providerTurns = turns.stream()
                .map(TurnReport::actual).filter(Objects::nonNull)
                .map(ActualTurn::interpretation).filter(Objects::nonNull)
                .filter(SearchInterpretationTelemetry::providerCalled).toList();
        long inputTokens = providerTurns.stream().map(SearchInterpretationTelemetry::inputTokens)
                .filter(Objects::nonNull).mapToLong(Integer::longValue).sum();
        long outputTokens = providerTurns.stream().map(SearchInterpretationTelemetry::outputTokens)
                .filter(Objects::nonNull).mapToLong(Integer::longValue).sum();
        List<Long> latencies = providerTurns.stream().map(SearchInterpretationTelemetry::durationMillis)
                .sorted().toList();
        Map<String, Long> methods = turns.stream().map(TurnReport::actual).filter(Objects::nonNull)
                .map(ActualTurn::interpretation).filter(Objects::nonNull)
                .collect(Collectors.groupingBy(SearchInterpretationTelemetry::method,
                        Collectors.counting()));
        Map<String, Long> failureReasons = turns.stream().map(TurnReport::actual).filter(Objects::nonNull)
                .map(ActualTurn::interpretation).filter(Objects::nonNull)
                .map(SearchInterpretationTelemetry::failureReason).filter(Objects::nonNull)
                .collect(Collectors.groupingBy(reason -> reason, Collectors.counting()));
        Map<String, CategoryMetrics> categories = cases.stream().flatMap(evaluationCase -> evaluationCase.tags().stream())
                .distinct().sorted().collect(Collectors.toMap(
                        tag -> tag,
                        tag -> categoryMetrics(cases.stream()
                                .filter(evaluationCase -> evaluationCase.tags().contains(tag)).toList()),
                        (left, right) -> left,
                        LinkedHashMap::new));
        Counts counts = counts(turns);
        return new RunMetrics(cases.size(), (int) cases.stream().filter(CaseReport::passed).count(),
                turns.size(), counts.passedTurns(), rate(counts.passedTurns(), turns.size()),
                counts.outcomeCorrect(), rate(counts.outcomeCorrect(), turns.size()),
                counts.criteriaExact(), rate(counts.criteriaExact(), turns.size()),
                counts.contextActionScored(), counts.contextActionCorrect(),
                rate(counts.contextActionCorrect(), counts.contextActionScored()),
                counts.groundingExact(), rate(counts.groundingExact(), turns.size()),
                counts.clarificationTruePositive(), counts.clarificationFalsePositive(),
                counts.clarificationFalseNegative(),
                rate(counts.clarificationTruePositive(),
                        counts.clarificationTruePositive() + counts.clarificationFalsePositive()),
                rate(counts.clarificationTruePositive(),
                        counts.clarificationTruePositive() + counts.clarificationFalseNegative()),
                providerTurns.size(), inputTokens, outputTokens, percentile(latencies, 0.50),
                percentile(latencies, 0.95), methods, failureReasons, categories);
    }

    private static CategoryMetrics categoryMetrics(List<CaseReport> cases) {
        List<TurnReport> turns = cases.stream().flatMap(evaluationCase -> evaluationCase.turns().stream()).toList();
        Counts counts = counts(turns);
        return new CategoryMetrics(cases.size(), (int) cases.stream().filter(CaseReport::passed).count(),
                turns.size(), counts.passedTurns(), rate(counts.passedTurns(), turns.size()),
                counts.outcomeCorrect(), rate(counts.outcomeCorrect(), turns.size()),
                counts.criteriaExact(), rate(counts.criteriaExact(), turns.size()),
                counts.contextActionScored(), counts.contextActionCorrect(),
                rate(counts.contextActionCorrect(), counts.contextActionScored()),
                counts.groundingExact(), rate(counts.groundingExact(), turns.size()),
                counts.clarificationTruePositive(), counts.clarificationFalsePositive(),
                counts.clarificationFalseNegative(),
                rate(counts.clarificationTruePositive(),
                        counts.clarificationTruePositive() + counts.clarificationFalsePositive()),
                rate(counts.clarificationTruePositive(),
                        counts.clarificationTruePositive() + counts.clarificationFalseNegative()));
    }

    private static Counts counts(List<TurnReport> turns) {
        int outcomeCorrect = 0;
        int criteriaExact = 0;
        int contextActionScored = 0;
        int contextActionCorrect = 0;
        int groundingExact = 0;
        int tp = 0;
        int fp = 0;
        int fn = 0;
        for (TurnReport turn : turns) {
            ActualTurn actual = turn.actual();
            if (actual == null) {
                if (Boolean.TRUE.equals(turn.expected().clarificationExpected())) fn++;
                continue;
            }
            if (turn.expected().outcome().equals(actual.outcome())) outcomeCorrect++;
            if (sorted(turn.expected().criteria()).equals(sorted(actual.criteria()))) criteriaExact++;
            if (turn.expected().contextAction() != null) {
                contextActionScored++;
                if (turn.expected().contextAction().equals(actual.contextAction())) contextActionCorrect++;
            }
            if (sorted(turn.expected().productNames()).equals(sorted(actual.productNames()))
                    && sorted(turn.expected().variantColors()).equals(sorted(actual.variantColors()))
                    && sorted(turn.expected().variants()).equals(sorted(actual.variants()))) groundingExact++;
            boolean expectedClarification = Boolean.TRUE.equals(turn.expected().clarificationExpected());
            if (expectedClarification && actual.clarification()) tp++;
            else if (!expectedClarification && actual.clarification()) fp++;
            else if (expectedClarification) fn++;
        }
        return new Counts((int) turns.stream().filter(TurnReport::passed).count(), outcomeCorrect, criteriaExact,
                contextActionScored, contextActionCorrect, groundingExact, tp, fp, fn);
    }

    private static Double rate(long numerator, long denominator) {
        return denominator == 0 ? null : (double) numerator / denominator;
    }

    private static Long percentile(List<Long> sortedValues, double percentile) {
        if (sortedValues.isEmpty()) return null;
        int index = (int) Math.ceil(percentile * sortedValues.size()) - 1;
        return sortedValues.get(Math.max(0, Math.min(index, sortedValues.size() - 1)));
    }

    private record Counts(
            int passedTurns, int outcomeCorrect, int criteriaExact, int contextActionScored,
            int contextActionCorrect, int groundingExact, int clarificationTruePositive,
            int clarificationFalsePositive, int clarificationFalseNegative) {
    }

    public record EvaluationReport(
            String suiteId, String corpusVersion, String catalogVersion, String corpusSha256,
            String catalogSha256, String applicationRevision, UUID runId, Instant completedAt,
            long durationMillis, RunMetrics metrics, List<CaseReport> cases) {
    }

    public record RunMetrics(
            int caseCount, int passedCaseCount, int turnCount, int passedTurnCount, Double turnPassRate,
            int outcomeCorrectTurns, Double outcomeAccuracy, int criteriaExactTurns, Double criteriaExactMatchRate,
            int contextActionScoredTurns, int contextActionCorrectTurns, Double contextActionAccuracy,
            int groundingExactTurns, Double groundingExactMatchRate,
            int clarificationTruePositive, int clarificationFalsePositive, int clarificationFalseNegative,
            Double clarificationPrecision, Double clarificationRecall,
            long providerCalls, long inputTokens, long outputTokens,
            Long providerLatencyP50Millis, Long providerLatencyP95Millis,
            Map<String, Long> interpretationMethods, Map<String, Long> interpretationFailureReasons,
            Map<String, CategoryMetrics> byCategory) {
    }

    public record CategoryMetrics(
            int caseCount, int passedCaseCount, int turnCount, int passedTurns, Double turnPassRate,
            int outcomeCorrectTurns, Double outcomeAccuracy, int criteriaExactTurns, Double criteriaExactMatchRate,
            int contextActionScoredTurns, int contextActionCorrectTurns, Double contextActionAccuracy,
            int groundingExactTurns, Double groundingExactMatchRate,
            int clarificationTruePositive, int clarificationFalsePositive, int clarificationFalseNegative,
            Double clarificationPrecision, Double clarificationRecall) {
    }

    public record CaseReport(String id, List<String> tags, boolean passed, long durationMillis, List<TurnReport> turns) {
    }

    public record TurnReport(
            int turn, String message, List<String> tags, String operation,
            EvaluationCorpus.ExpectedTurn expected, ActualTurn actual,
            boolean passed, List<String> mismatches, long durationMillis) {
    }

    public record ActualTurn(
            String outcome, List<String> criteria, List<String> productNames, List<String> variantColors,
            List<String> variants, long totalResults, List<ProductFact> products, String reply,
            String contextAction, boolean clarification, SearchInterpretationTelemetry interpretation) {
    }

    public record ProductFact(UUID id, String name, String category, List<VariantFact> variants) {
    }

    public record VariantFact(UUID id, String color, String size, BigDecimal price, int stock) {
    }
}
