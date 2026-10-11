package dev.julioperez.nls.evaluation.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class EvaluationCorpusTest {
    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void partitionsPilotDevelopmentAndHoldoutByWholeConversation() throws IOException {
        JsonNode source;
        try (var input = new ClassPathResource("evaluation/catalog-conversation-v1.1.1.json").getInputStream()) {
            source = mapper.readTree(input.readAllBytes());
        }

        var pilot = EvaluationCorpus.select(mapper, source, "catalog-conversation-pilot");
        var development = EvaluationCorpus.select(mapper, source, "catalog-conversation-development");
        var holdout = EvaluationCorpus.select(mapper, source, "catalog-conversation-holdout");

        assertThat(source.path("version").stringValue()).isEqualTo("1.1.1");
        assertThat(source.path("catalogVersion").stringValue()).isEqualTo("catalog-1.1.0");
        assertThat(source.path("cases").size()).isEqualTo(60);
        int totalTurns = 0;
        for (JsonNode evaluationCase : source.path("cases")) {
            totalTurns += evaluationCase.path("turns").size();
        }
        assertThat(totalTurns).isEqualTo(207);

        assertThat(pilot.cases()).hasSize(12);
        assertThat(development.cases()).hasSize(45);
        assertThat(holdout.cases()).hasSize(15);
        assertThat(development.cases().stream().map(EvaluationCorpus.EvaluationCase::id))
                .containsAll(pilot.cases().stream().map(EvaluationCorpus.EvaluationCase::id).toList());
        assertThat(holdout.cases().stream().map(EvaluationCorpus.EvaluationCase::id))
                .doesNotContainAnyElementsOf(development.cases().stream()
                        .map(EvaluationCorpus.EvaluationCase::id).toList());
        assertThat(development.cases().stream().mapToInt(item -> item.turns().size()).sum()
                + holdout.cases().stream().mapToInt(item -> item.turns().size()).sum()).isEqualTo(207);
        assertThat(holdout.cases().stream().map(EvaluationCorpus.EvaluationCase::tags).toList())
                .anySatisfy(tags -> assertThat(tags).contains("typo"))
                .anySatisfy(tags -> assertThat(tags).contains("ambiguity"))
                .anySatisfy(tags -> assertThat(tags).contains("multilingual"));
    }

    @Test
    void rejectsUnknownSuiteIds() throws IOException {
        JsonNode source;
        try (var input = new ClassPathResource("evaluation/catalog-conversation-v1.1.1.json").getInputStream()) {
            source = mapper.readTree(input.readAllBytes());
        }
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> EvaluationCorpus.select(mapper, source, "arbitrary-file-name"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
