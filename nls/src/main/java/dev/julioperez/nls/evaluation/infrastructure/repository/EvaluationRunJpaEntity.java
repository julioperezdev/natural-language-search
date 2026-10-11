package dev.julioperez.nls.evaluation.infrastructure.repository;

import dev.julioperez.nls.evaluation.domain.EvaluationRunStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "evaluation_runs")
public class EvaluationRunJpaEntity {
    @Id
    private UUID id;

    @Column(name = "suite_id", nullable = false, length = 120)
    private String suiteId;

    @Column(name = "corpus_version", nullable = false, length = 80)
    private String corpusVersion;

    @Column(name = "catalog_version", nullable = false, length = 80)
    private String catalogVersion;

    @Column(name = "corpus_sha256", nullable = false, length = 64)
    private String corpusSha256;

    @Column(name = "catalog_sha256", nullable = false, length = 64)
    private String catalogSha256;

    @Column(nullable = false, length = 24)
    private String status;

    @Column(name = "model_id", nullable = false, length = 120)
    private String modelId;

    @Column(name = "application_revision", nullable = false, length = 120)
    private String applicationRevision;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "case_count", nullable = false)
    private int caseCount;

    @Column(name = "passed_case_count", nullable = false)
    private int passedCaseCount;

    @Column(name = "failed_case_count", nullable = false)
    private int failedCaseCount;

    @Column(name = "report_json", columnDefinition = "TEXT")
    private String reportJson;

    @Column(name = "failure_code", length = 80)
    private String failureCode;

    protected EvaluationRunJpaEntity() {
    }

    public EvaluationRunJpaEntity(
            UUID id, String suiteId, String corpusVersion, String catalogVersion,
            String corpusSha256, String catalogSha256, String modelId,
            String applicationRevision, Instant createdAt) {
        this.id = id;
        this.suiteId = suiteId;
        this.corpusVersion = corpusVersion;
        this.catalogVersion = catalogVersion;
        this.corpusSha256 = corpusSha256;
        this.catalogSha256 = catalogSha256;
        this.modelId = modelId;
        this.applicationRevision = applicationRevision;
        this.createdAt = createdAt;
        this.status = EvaluationRunStatus.QUEUED.name();
    }

    public void markRunning(Instant at) {
        this.status = EvaluationRunStatus.RUNNING.name();
        this.startedAt = at;
    }

    public void complete(int cases, int passed, int failed, String report, Instant at) {
        this.status = EvaluationRunStatus.COMPLETED.name();
        this.caseCount = cases;
        this.passedCaseCount = passed;
        this.failedCaseCount = failed;
        this.reportJson = report;
        this.finishedAt = at;
    }

    public void fail(String code, Instant at) {
        this.status = EvaluationRunStatus.FAILED.name();
        this.failureCode = code;
        this.finishedAt = at;
    }

    public UUID getId() { return id; }
    public String getSuiteId() { return suiteId; }
    public String getCorpusVersion() { return corpusVersion; }
    public String getCatalogVersion() { return catalogVersion; }
    public String getCorpusSha256() { return corpusSha256; }
    public String getCatalogSha256() { return catalogSha256; }
    public EvaluationRunStatus getStatus() { return EvaluationRunStatus.valueOf(status); }
    public String getModelId() { return modelId; }
    public String getApplicationRevision() { return applicationRevision; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public int getCaseCount() { return caseCount; }
    public int getPassedCaseCount() { return passedCaseCount; }
    public int getFailedCaseCount() { return failedCaseCount; }
    public String getReportJson() { return reportJson; }
    public String getFailureCode() { return failureCode; }
}
