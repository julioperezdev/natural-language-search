package dev.julioperez.nls.evaluation.application;

import dev.julioperez.nls.evaluation.domain.EvaluationRunStatus;
import java.time.Instant;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

public record EvaluationRunResponse(
        UUID id,
        String suiteId,
        String corpusVersion,
        String catalogVersion,
        String corpusSha256,
        String catalogSha256,
        EvaluationRunStatus status,
        String modelId,
        String applicationRevision,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt,
        int caseCount,
        int passedCaseCount,
        int failedCaseCount,
        String failureCode,
        JsonNode report) {
}
