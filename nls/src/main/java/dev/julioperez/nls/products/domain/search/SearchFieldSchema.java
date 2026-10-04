package dev.julioperez.nls.products.domain.search;

import java.util.List;

public record SearchFieldSchema(
        String name,
        String description,
        SearchFieldType type,
        List<String> operators,
        List<String> values,
        boolean filterable,
        boolean sortable) {
    public SearchFieldSchema {
        operators = List.copyOf(operators);
        values = List.copyOf(values);
    }
}
