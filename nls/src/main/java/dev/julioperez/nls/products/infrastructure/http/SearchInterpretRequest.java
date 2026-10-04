package dev.julioperez.nls.products.infrastructure.http;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SearchInterpretRequest(@NotBlank @Size(max = 2000) String message) {
}
