package dev.julioperez.nls.evaluation.application;

import dev.julioperez.nls.evaluation.infrastructure.EvaluationProperties;
import dev.julioperez.nls.evaluation.infrastructure.repository.EvaluationRunJpaEntity;
import dev.julioperez.nls.evaluation.infrastructure.repository.EvaluationRunJpaRepository;
import dev.julioperez.nls.products.infrastructure.ai.typesafe.TypeSafeSearchProperties;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import javax.sql.DataSource;

@Service
@ConditionalOnProperty(prefix = "nls.evaluation", name = "enabled", havingValue = "true")
@Profile({"local", "test"})
public class EvaluationRunService {
    private final EvaluationRunJpaRepository runs;
    private final EvaluationProperties properties;
    private final ResourceLoader resources;
    private final JsonMapper mapper;
    private final TypeSafeSearchProperties modelProperties;
    private final EvaluationRunWorker worker;
    private final TaskExecutor executor;
    private final DataSource dataSource;
    private final String applicationRevision;

    public EvaluationRunService(
            EvaluationRunJpaRepository runs,
            EvaluationProperties properties,
            ResourceLoader resources,
            JsonMapper mapper,
            TypeSafeSearchProperties modelProperties,
            EvaluationRunWorker worker,
            @Qualifier("evaluationTaskExecutor") TaskExecutor executor,
            DataSource dataSource,
            @Value("${nls.build.revision:unknown}") String applicationRevision) {
        this.runs = runs;
        this.properties = properties;
        this.resources = resources;
        this.mapper = mapper;
        this.modelProperties = modelProperties;
        this.worker = worker;
        this.executor = executor;
        this.dataSource = dataSource;
        this.applicationRevision = applicationRevision;
    }

    public EvaluationRunResponse start(String requestedSuiteId) {
        verifyEvaluationDatabase();
        if (applicationRevision == null || applicationRevision.isBlank() || "unknown".equalsIgnoreCase(applicationRevision)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Set nls.build.revision before starting an evaluation run.");
        }
        CorpusDocument source = loadCorpusDocument();
        EvaluationCorpus corpus;
        try {
            boolean suiteExists = EvaluationCorpus.suites(mapper, source.json()).stream()
                    .anyMatch(suite -> suite.id().equals(requestedSuiteId));
            if (!suiteExists) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Evaluation suite not found.");
            }
            corpus = EvaluationCorpus.select(mapper, source.json(), requestedSuiteId);
        } catch (IOException exception) {
            throw new IllegalStateException("Evaluation corpus could not be loaded.", exception);
        }
        String corpusSha256 = sha256(source.bytes());
        String catalogSha256 = sha256(loadCatalogFixture());
        UUID id = UUID.randomUUID();
        EvaluationRunJpaEntity run = runs.saveAndFlush(new EvaluationRunJpaEntity(
                id, corpus.suiteId(), corpus.version(), corpus.catalogVersion(), corpusSha256,
                catalogSha256, modelProperties.model(), applicationRevision, Instant.now()));
        try {
            executor.execute(() -> worker.execute(id, corpus));
        } catch (RejectedExecutionException exception) {
            runs.findById(id).ifPresent(queuedRun -> {
                queuedRun.fail("RUN_QUEUE_FULL", Instant.now());
                runs.saveAndFlush(queuedRun);
            });
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Evaluation runner is busy; retry later.");
        }
        return response(run);
    }

    public EvaluationSuites suites() {
        CorpusDocument source = loadCorpusDocument();
        try {
            JsonNode json = source.json();
            List<EvaluationSuiteDescriptor> definitions = EvaluationCorpus.suites(mapper, json).stream()
                    .map(suite -> new EvaluationSuiteDescriptor(
                            suite.id(), suite.description(), suite.caseIds().size()))
                    .toList();
            return new EvaluationSuites(json.path("version").stringValue(),
                    json.path("catalogVersion").stringValue(), definitions);
        } catch (IOException exception) {
            throw new IllegalStateException("Evaluation corpus could not be loaded.", exception);
        }
    }

    private void verifyEvaluationDatabase() {
        try (var connection = dataSource.getConnection()) {
            String url = connection.getMetaData().getURL();
            var matcher = java.util.regex.Pattern.compile("^jdbc:postgresql://([^/:?]+)",
                    java.util.regex.Pattern.CASE_INSENSITIVE).matcher(url == null ? "" : url);
            if (!matcher.find()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Evaluation runs require an explicitly allowed development database host.");
            }
            String host = matcher.group(1).toLowerCase(Locale.ROOT);
            boolean allowed = Arrays.stream(properties.allowedDatabaseHosts().split(","))
                    .map(String::strip)
                    .map(value -> value.toLowerCase(Locale.ROOT))
                    .anyMatch(host::equals);
            if (!allowed) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Evaluation runs require an explicitly allowed development database host.");
            }
        } catch (SQLException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Evaluation database is unavailable.");
        }
    }

    @Transactional(readOnly = true)
    public EvaluationRunResponse get(UUID id) {
        return response(runs.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Evaluation run not found.")));
    }

    @Transactional(readOnly = true)
    public EvaluationRunPage list(int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        var result = runs.findAllByOrderByCreatedAtDesc(
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt")));
        List<EvaluationRunSummary> summaries = result.getContent().stream()
                .map(entity -> new EvaluationRunSummary(entity.getId(), entity.getSuiteId(),
                        entity.getCorpusVersion(), entity.getCatalogVersion(), entity.getStatus(), entity.getModelId(),
                        entity.getApplicationRevision(), entity.getCreatedAt(), entity.getFinishedAt(),
                        entity.getCaseCount(), entity.getPassedCaseCount(), entity.getFailedCaseCount(),
                        entity.getFailureCode()))
                .toList();
        return new EvaluationRunPage(summaries, result.getTotalElements(), result.getNumber(), result.getSize());
    }

    private CorpusDocument loadCorpusDocument() {
        try (var input = resources.getResource(properties.corpusLocation()).getInputStream()) {
            byte[] bytes = input.readAllBytes();
            JsonNode json = mapper.readTree(bytes);
            EvaluationCorpus.suites(mapper, json);
            return new CorpusDocument(json, bytes);
        } catch (IOException exception) {
            throw new IllegalStateException("Evaluation corpus could not be loaded.", exception);
        }
    }

    private byte[] loadCatalogFixture() {
        try (var input = resources.getResource(properties.catalogFixtureLocation()).getInputStream()) {
            return input.readAllBytes();
        } catch (IOException exception) {
            throw new IllegalStateException("Evaluation catalog fixture could not be loaded.", exception);
        }
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private EvaluationRunResponse response(EvaluationRunJpaEntity entity) {
        JsonNode report = null;
        if (entity.getReportJson() != null) {
            try {
                report = mapper.readTree(entity.getReportJson());
            } catch (RuntimeException exception) {
                throw new IllegalStateException("Stored evaluation report is invalid JSON.", exception);
            }
        }
        return new EvaluationRunResponse(entity.getId(), entity.getSuiteId(), entity.getCorpusVersion(),
                entity.getCatalogVersion(), entity.getCorpusSha256(), entity.getCatalogSha256(),
                entity.getStatus(), entity.getModelId(), entity.getApplicationRevision(), entity.getCreatedAt(),
                entity.getStartedAt(), entity.getFinishedAt(), entity.getCaseCount(), entity.getPassedCaseCount(),
                entity.getFailedCaseCount(), entity.getFailureCode(), report);
    }

    public record EvaluationRunPage(
            List<EvaluationRunSummary> items, long total, int page, int size) {
    }

    public record EvaluationSuites(
            String corpusVersion, String catalogVersion, List<EvaluationSuiteDescriptor> suites) {
    }

    public record EvaluationSuiteDescriptor(String id, String description, int caseCount) {
    }

    private record CorpusDocument(JsonNode json, byte[] bytes) {
    }
}
