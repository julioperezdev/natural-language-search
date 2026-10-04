package dev.julioperez.nls.products.domain.search;

import java.util.List;

public record SearchSchema(String context, List<SearchFieldSchema> fields, SearchPaginationSchema pagination) {
    public SearchSchema {
        fields = List.copyOf(fields);
    }
}
