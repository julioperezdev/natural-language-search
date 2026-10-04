package dev.julioperez.nls.productsearchresponses.infrastructure.http;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record HumanizeProductSearchResponseRequest(
        @NotBlank @Size(max = 2000) String message,
        @NotNull @Valid ProductSearchResultsRequest results) {
}
