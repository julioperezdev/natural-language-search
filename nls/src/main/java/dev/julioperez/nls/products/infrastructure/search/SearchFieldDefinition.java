package dev.julioperez.nls.products.infrastructure.search;

import dev.julioperez.nls.products.domain.search.FilterOperator;
import dev.julioperez.nls.products.domain.search.SearchFieldType;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

public record SearchFieldDefinition(
        String name,
        String description,
        SearchFieldType type,
        Class<?> javaType,
        Set<FilterOperator> allowedOperators,
        boolean sortable,
        boolean requiresDistinct,
        Supplier<List<String>> valuesProvider,
        PathResolver pathResolver) {
    public SearchFieldDefinition {
        allowedOperators = Set.copyOf(allowedOperators);
    }

    public List<String> values() {
        return valuesProvider == null ? List.of() : List.copyOf(valuesProvider.get());
    }
}
