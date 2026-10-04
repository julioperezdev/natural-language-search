package dev.julioperez.nls.products.infrastructure.search;

import dev.julioperez.nls.products.domain.search.Criteria;
import dev.julioperez.nls.products.domain.search.Filter;
import dev.julioperez.nls.products.domain.search.FilterOperator;
import dev.julioperez.nls.products.domain.search.Order;
import dev.julioperez.nls.products.domain.search.ProductSearchException;
import dev.julioperez.nls.products.domain.search.SearchFieldSchema;
import dev.julioperez.nls.products.domain.search.SearchFieldType;
import dev.julioperez.nls.products.domain.search.SearchPaginationSchema;
import dev.julioperez.nls.products.domain.search.SearchSchema;
import dev.julioperez.nls.products.domain.search.SortDirection;
import dev.julioperez.nls.products.infrastructure.repository.postgres.CategoryBaseJpaRepository;
import dev.julioperez.nls.products.infrastructure.repository.postgres.ProductVariantBaseJpaRepository;
import dev.julioperez.nls.products.infrastructure.repository.postgres.ProductJpaEntity;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class ProductSearchFieldRegistry {
    public static final int DEFAULT_LIMIT = 10;
    public static final int MAX_LIMIT = 50;
    public static final int MAX_OFFSET = 10_000;
    public static final int MAX_FILTERS = 20;
    public static final int MAX_IN_VALUES = 50;
    public static final int MAX_TEXT_LENGTH = 200;

    private final Map<String, SearchFieldDefinition> fields;

    public ProductSearchFieldRegistry(
            CategoryBaseJpaRepository categories,
            ProductVariantBaseJpaRepository variants) {
        Map<String, SearchFieldDefinition> definitions = new LinkedHashMap<>();
        add(definitions, new SearchFieldDefinition(
                "productName", "Product name", SearchFieldType.STRING, String.class,
                Set.of(FilterOperator.EQUALS, FilterOperator.NOT_EQUALS,
                        FilterOperator.CONTAINS, FilterOperator.NOT_CONTAINS),
                true, false, null, (root, joins) -> root.get("name")));
        add(definitions, new SearchFieldDefinition(
                "category", "Product category", SearchFieldType.ENUM, String.class,
                Set.of(FilterOperator.EQUALS, FilterOperator.IN),
                true, false, categories::findSearchValues,
                (root, joins) -> joins.join("category").get("name")));
        add(definitions, new SearchFieldDefinition(
                "color", "Variant color", SearchFieldType.ENUM, String.class,
                Set.of(FilterOperator.EQUALS, FilterOperator.IN),
                false, true, variants::findSearchColors,
                (root, joins) -> joins.join("variants").get("color")));
        add(definitions, new SearchFieldDefinition(
                "size", "Variant size", SearchFieldType.ENUM, String.class,
                Set.of(FilterOperator.EQUALS, FilterOperator.IN),
                false, true, variants::findSearchSizes,
                (root, joins) -> joins.join("variants").get("size")));
        add(definitions, new SearchFieldDefinition(
                "price", "Variant price", SearchFieldType.NUMBER, BigDecimal.class,
                Set.of(FilterOperator.EQUALS, FilterOperator.LESS_THAN,
                        FilterOperator.LESS_THAN_OR_EQUAL, FilterOperator.GREATER_THAN,
                        FilterOperator.GREATER_THAN_OR_EQUAL),
                false, true, null,
                (root, joins) -> joins.join("variants").get("price")));
        add(definitions, new SearchFieldDefinition(
                "stock", "Variant stock", SearchFieldType.INTEGER, Integer.class,
                Set.of(FilterOperator.EQUALS, FilterOperator.GREATER_THAN,
                        FilterOperator.LESS_THAN, FilterOperator.GREATER_THAN_OR_EQUAL,
                        FilterOperator.LESS_THAN_OR_EQUAL),
                false, true, null,
                (root, joins) -> joins.join("variants").get("stock")));
        this.fields = Map.copyOf(definitions);
    }

    public SearchSchema schema() {
        List<SearchFieldSchema> schemaFields = fields.values().stream()
                .sorted(Comparator.comparing(SearchFieldDefinition::name))
                .map(field -> new SearchFieldSchema(
                        field.name(),
                        field.description(),
                        field.type(),
                        field.allowedOperators().stream()
                                .map(FilterOperator::value)
                                .sorted()
                                .toList(),
                        field.values(),
                        true,
                        field.sortable()))
                .toList();
        return new SearchSchema("product-search", schemaFields,
                new SearchPaginationSchema(DEFAULT_LIMIT, MAX_LIMIT));
    }

    public Criteria validate(Criteria criteria) {
        if (criteria == null) {
            throw invalid("INVALID_SEARCH_REQUEST", "Search criteria are required.");
        }
        if (criteria.filters().size() > MAX_FILTERS) {
            throw invalid("SEARCH_LIMIT_INVALID", "At most 20 filters are allowed.");
        }
        if (criteria.limit() < 0 || criteria.limit() > MAX_LIMIT) {
            throw invalid("SEARCH_LIMIT_INVALID", "Limit must be between 0 and 50.");
        }
        if (criteria.offset() < 0 || criteria.offset() > MAX_OFFSET) {
            throw invalid("SEARCH_LIMIT_INVALID", "Offset must be between 0 and 10000.");
        }

        Map<String, Set<String>> allowedValues = new LinkedHashMap<>();
        List<Filter> filters = new ArrayList<>();
        for (Filter filter : criteria.filters()) {
            if (filter == null) {
                throw invalid("INVALID_SEARCH_REQUEST", "Null filters are not allowed.");
            }
            SearchFieldDefinition definition = fields.get(filter.field());
            if (definition == null) {
                throw invalid("SEARCH_FIELD_NOT_ALLOWED", "Search field is not available.");
            }
            if (filter.operator() == null || !definition.allowedOperators().contains(filter.operator())) {
                throw invalid("SEARCH_OPERATOR_NOT_ALLOWED", "Operator is not available for this field.");
            }
            Object value = normalizeValue(filter.value(), definition, filter.operator(), allowedValues);
            filters.add(new Filter(filter.field(), filter.operator(), value));
        }

        Order order = criteria.order();
        if (order != null) {
            SearchFieldDefinition definition = fields.get(order.field());
            if (definition == null || !definition.sortable()) {
                throw invalid("SEARCH_ORDER_NOT_ALLOWED", "Search field cannot be used for ordering.");
            }
            if (order.direction() == null) {
                throw invalid("SEARCH_ORDER_NOT_ALLOWED", "Sort direction is required.");
            }
        }

        return new Criteria(filters, order, criteria.limit(), criteria.offset());
    }

    public SearchFieldDefinition get(String name) {
        SearchFieldDefinition definition = fields.get(name);
        if (definition == null) {
            throw invalid("SEARCH_FIELD_NOT_ALLOWED", "Search field is not available.");
        }
        return definition;
    }

    public boolean requiresDistinct(Criteria criteria) {
        return criteria.filters().stream()
                .map(filter -> get(filter.field()))
                .anyMatch(SearchFieldDefinition::requiresDistinct);
    }

    public Path<?> resolve(SearchFieldDefinition field, Root<ProductJpaEntity> root, JoinRegistry joins) {
        return field.pathResolver().resolve(root, joins);
    }

    private Object normalizeValue(
            Object raw,
            SearchFieldDefinition field,
            FilterOperator operator,
            Map<String, Set<String>> allowedValues) {
        if (raw == null) {
            throw invalid("SEARCH_VALUE_INVALID", "Filter value is required.");
        }
        if (operator == FilterOperator.IN || operator == FilterOperator.NOT_IN
                || operator == FilterOperator.INCLUDES || operator == FilterOperator.INCLUDES_OR) {
            List<?> rawValues = asValues(raw);
            if (rawValues.isEmpty() || rawValues.size() > MAX_IN_VALUES) {
                throw invalid("SEARCH_VALUE_INVALID", "List filter must contain between 1 and 50 values.");
            }
            return rawValues.stream()
                    .map(value -> normalizeScalar(value, field, allowedValues))
                    .toList();
        }
        return normalizeScalar(raw, field, allowedValues);
    }

    private Object normalizeScalar(Object raw, SearchFieldDefinition field, Map<String, Set<String>> allowedValues) {
        try {
            return switch (field.type()) {
                case STRING -> stringValue(raw);
                case ENUM -> canonicalEnum(raw, field, allowedValues);
                case NUMBER -> decimalValue(raw);
                case INTEGER -> integerValue(raw);
            };
        } catch (ProductSearchException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw invalid("SEARCH_VALUE_INVALID", "Filter value does not match the field type.");
        }
    }

    private String canonicalEnum(Object raw, SearchFieldDefinition field, Map<String, Set<String>> cache) {
        String requested = stringValue(raw);
        Set<String> values = cache.computeIfAbsent(field.name(), ignored -> Set.copyOf(field.values()));
        return values.stream()
                .filter(value -> value.equalsIgnoreCase(requested))
                .findFirst()
                .orElseThrow(() -> invalid("SEARCH_VALUE_INVALID", "Value is not available for this field."));
    }

    private String stringValue(Object value) {
        if (!(value instanceof String string) || string.isBlank() || string.length() > MAX_TEXT_LENGTH) {
            throw invalid("SEARCH_VALUE_INVALID", "Text filter value is invalid.");
        }
        return string;
    }

    private BigDecimal decimalValue(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        if (value instanceof String string && !string.isBlank()) {
            return new BigDecimal(string.trim());
        }
        throw invalid("SEARCH_VALUE_INVALID", "Numeric filter value is invalid.");
    }

    private Integer integerValue(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal.intValueExact();
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString()).intValueExact();
        }
        if (value instanceof String string && !string.isBlank()) {
            return Integer.valueOf(string.trim());
        }
        throw invalid("SEARCH_VALUE_INVALID", "Integer filter value is invalid.");
    }

    private List<?> asValues(Object raw) {
        if (raw instanceof Collection<?> values) {
            return new ArrayList<>(values);
        }
        if (raw instanceof String string) {
            return List.of(string.split(",", -1));
        }
        return List.of(raw);
    }

    private void add(Map<String, SearchFieldDefinition> definitions, SearchFieldDefinition field) {
        if (definitions.putIfAbsent(field.name(), field) != null) {
            throw new IllegalStateException("Duplicate search field: " + field.name());
        }
    }

    private ProductSearchException invalid(String code, String message) {
        return new ProductSearchException(code, message);
    }
}
