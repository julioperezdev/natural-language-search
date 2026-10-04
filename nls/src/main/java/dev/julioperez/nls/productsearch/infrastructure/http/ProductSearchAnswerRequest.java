package dev.julioperez.nls.productsearch.infrastructure.http;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProductSearchAnswerRequest(@NotBlank @Size(max = 2000) String message) {
}
