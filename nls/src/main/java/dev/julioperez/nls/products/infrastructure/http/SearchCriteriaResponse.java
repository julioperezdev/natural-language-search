package dev.julioperez.nls.products.infrastructure.http;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record SearchCriteriaResponse(
        List<SearchFilterResponse> filters,
        @JsonProperty("order_by") String orderBy,
        String order,
        int limit,
        int offset) {
    public SearchCriteriaResponse {
        filters = List.copyOf(filters);
    }
}
