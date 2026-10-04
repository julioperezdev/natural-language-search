package dev.julioperez.nls.products.infrastructure.http;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ProductSearchRequest(
        List<@Valid SearchFilterRequest> filters,
        @JsonProperty("order_by") String orderBy,
        String order,
        @Min(0) @Max(50) Integer limit,
        @Min(0) @Max(10000) Integer offset,
        @Size(max = 2000) String message) {
}
