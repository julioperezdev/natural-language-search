package dev.julioperez.nls.evaluation.application;

import dev.julioperez.nls.evaluation.domain.EvaluationRunStatus;
import java.time.Instant;
import java.util.UUID;

public record EvaluationRunSummary(
        UUID id,
        String suiteId,
        String corpusVersion,
        String catalogVersion,
        EvaluationRunStatus status,
        String modelId,
        String applicationRevision,
        Instant createdAt,
        Instant finishedAt,
        int caseCount,
        int passedCaseCount,
        int failedCaseCount,
        String failureCode) {
}
