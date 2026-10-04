package dev.julioperez.nls.products.infrastructure.http;

import tools.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SearchFilterRequest(
        @NotBlank String field,
        @NotBlank String operator,
        @NotNull JsonNode value) {
}
