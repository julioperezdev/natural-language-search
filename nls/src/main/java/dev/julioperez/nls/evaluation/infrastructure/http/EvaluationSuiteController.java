package dev.julioperez.nls.evaluation.infrastructure.http;

import dev.julioperez.nls.evaluation.application.EvaluationRunService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/evaluations/suites")
@Tag(name = "Evaluations", description = "Versioned evaluation suites available for local runs.")
@ConditionalOnProperty(prefix = "nls.evaluation", name = "enabled", havingValue = "true")
@Profile({"local", "test"})
public class EvaluationSuiteController {
    private final EvaluationRunService evaluations;

    public EvaluationSuiteController(EvaluationRunService evaluations) {
        this.evaluations = evaluations;
    }

    @GetMapping
    @Operation(summary = "List evaluation suites", description = "Lists the pilot, development, and holdout suites.")
    public EvaluationRunService.EvaluationSuites list() {
        return evaluations.suites();
    }
}
