package dev.julioperez.nls.products.application;

import dev.julioperez.nls.products.domain.search.Criteria;

public record SearchInterpretationResult(Criteria criteria, SearchInterpretationTelemetry telemetry) {
}
