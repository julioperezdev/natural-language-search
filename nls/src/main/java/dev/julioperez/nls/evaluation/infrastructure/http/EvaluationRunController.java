package dev.julioperez.nls.evaluation.infrastructure.http;

import dev.julioperez.nls.evaluation.application.EvaluationRunResponse;
import dev.julioperez.nls.evaluation.application.EvaluationRunService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/evaluations/runs")
@Tag(name = "Evaluations", description = "Run and inspect the versioned product-search evaluation corpus.")
@ConditionalOnProperty(prefix = "nls.evaluation", name = "enabled", havingValue = "true")
@Profile({"local", "test"})
public class EvaluationRunController {
    private final EvaluationRunService evaluations;

    public EvaluationRunController(EvaluationRunService evaluations) {
        this.evaluations = evaluations;
    }

    @PostMapping
    @Operation(summary = "Start an evaluation run", description = "Starts the configured corpus asynchronously.")
    public ResponseEntity<EvaluationRunResponse> start(@Valid @RequestBody StartEvaluationRequest request) {
        EvaluationRunResponse response = evaluations.start(request.suiteId());
        return ResponseEntity.accepted()
                .location(URI.create("/api/evaluations/runs/" + response.id()))
                .body(response);
    }

    @GetMapping
    @Operation(summary = "List evaluation runs", description = "Lists recent run summaries in reverse chronological order.")
    public EvaluationRunService.EvaluationRunPage list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return evaluations.list(page, size);
    }

    @GetMapping("/{runId}")
    @Operation(summary = "Get an evaluation run", description = "Returns status, metrics and the persisted report.")
    public EvaluationRunResponse get(@PathVariable UUID runId) {
        return evaluations.get(runId);
    }

    public record StartEvaluationRequest(@NotBlank String suiteId) {
    }
}
