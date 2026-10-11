package dev.julioperez.nls.evaluation.application;

import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** A validated, suite-specific view of a versioned evaluation corpus. */
public record EvaluationCorpus(
        String suiteId,
        String version,
        String catalogVersion,
        List<EvaluationCase> cases) {

    public EvaluationCorpus {
        cases = List.copyOf(cases);
    }

    public record EvaluationCase(String id, List<String> tags, List<EvaluationTurn> turns) {
        public EvaluationCase {
            tags = tags == null ? List.of() : List.copyOf(tags);
            turns = turns == null ? List.of() : List.copyOf(turns);
        }
    }

    public record EvaluationTurn(String message, String operation, ExpectedTurn expected) {
        public EvaluationTurn {
            operation = operation == null || operation.isBlank() ? "MESSAGE" : operation.strip();
        }
    }

    public record ExpectedTurn(
            String outcome,
            List<String> criteria,
            List<String> productNames,
            List<String> variantColors,
            List<String> variants,
            String contextAction,
            Boolean clarificationExpected,
            String clarificationReason) {
        public ExpectedTurn {
            criteria = criteria == null ? List.of() : List.copyOf(criteria);
            productNames = productNames == null ? List.of() : List.copyOf(productNames);
            variantColors = variantColors == null ? List.of() : List.copyOf(variantColors);
            variants = variants == null ? List.of() : List.copyOf(variants);
            clarificationExpected = clarificationExpected == null
                    ? "NEEDS_CLARIFICATION".equals(outcome)
                    : clarificationExpected;
        }
    }

    public record SuiteDefinition(String id, String description, List<String> caseIds) {
        public SuiteDefinition {
            caseIds = caseIds == null ? List.of() : List.copyOf(caseIds);
        }
    }

    public static List<SuiteDefinition> suites(JsonMapper mapper, JsonNode json) throws IOException {
        List<SuiteDefinition> definitions = mapper.readerForListOf(SuiteDefinition.class)
                .readValue(json.path("suites"));
        List<EvaluationCase> cases = mapper.readerForListOf(EvaluationCase.class)
                .readValue(json.path("cases"));
        validate(json, definitions, cases);
        return List.copyOf(definitions);
    }

    public static EvaluationCorpus select(
            JsonMapper mapper, JsonNode json, String requestedSuiteId) throws IOException {
        List<SuiteDefinition> definitions = suites(mapper, json);
        SuiteDefinition definition = definitions.stream()
                .filter(candidate -> candidate.id().equals(requestedSuiteId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Evaluation suite not found."));
        Map<String, EvaluationCase> byId = new LinkedHashMap<>();
        List<EvaluationCase> allCases = mapper.readerForListOf(EvaluationCase.class)
                .readValue(json.path("cases"));
        for (EvaluationCase evaluationCase : allCases) {
            byId.put(evaluationCase.id(), evaluationCase);
        }
        List<EvaluationCase> selected = definition.caseIds().stream().map(byId::get).toList();
        return new EvaluationCorpus(
                requestedSuiteId,
                json.path("version").stringValue(),
                json.path("catalogVersion").stringValue(),
                selected);
    }

    private static void validate(
            JsonNode json, List<SuiteDefinition> suites, List<EvaluationCase> cases) {
        if (json.path("version").stringValue().isBlank()
                || json.path("catalogVersion").stringValue().isBlank()
                || suites == null || suites.isEmpty() || cases == null || cases.isEmpty()) {
            throw new IllegalArgumentException("Evaluation corpus metadata or cases are empty.");
        }

        Set<String> caseIds = new HashSet<>();
        for (EvaluationCase evaluationCase : cases) {
            if (evaluationCase.id() == null || evaluationCase.id().isBlank()
                    || !caseIds.add(evaluationCase.id()) || evaluationCase.turns().isEmpty()) {
                throw new IllegalArgumentException("Evaluation case IDs must be unique and cases must have turns.");
            }
            for (EvaluationTurn turn : evaluationCase.turns()) {
                if (turn.message() == null || turn.message().isBlank() || turn.expected() == null
                        || turn.expected().outcome() == null || turn.expected().outcome().isBlank()) {
                    throw new IllegalArgumentException("Every evaluation turn needs a message and expected outcome.");
                }
                if (!Set.of("MESSAGE", "RESET_CONTEXT").contains(turn.operation())) {
                    throw new IllegalArgumentException("Evaluation turn operation is not supported.");
                }
            }
        }

        Set<String> suiteIds = new HashSet<>();
        for (SuiteDefinition suite : suites) {
            if (suite.id() == null || suite.id().isBlank() || !suiteIds.add(suite.id())
                    || suite.caseIds().isEmpty() || new HashSet<>(suite.caseIds()).size() != suite.caseIds().size()
                    || !caseIds.containsAll(suite.caseIds())) {
                throw new IllegalArgumentException("Evaluation suites must have unique IDs and known case IDs.");
            }
        }
    }
}
