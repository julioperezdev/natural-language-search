package dev.julioperez.nls.products.infrastructure.http;

import tools.jackson.databind.JsonNode;
import dev.julioperez.nls.products.domain.search.Criteria;
import dev.julioperez.nls.products.domain.search.Filter;
import dev.julioperez.nls.products.domain.search.FilterOperator;
import dev.julioperez.nls.products.domain.search.Order;
import dev.julioperez.nls.products.domain.search.ProductSearchException;
import dev.julioperez.nls.products.domain.search.SortDirection;
import dev.julioperez.nls.products.domain.search.Order;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ProductSearchCriteriaAdapter {
    public SearchCriteriaResponse toResponse(Criteria criteria) {
        Order order = criteria.order();
        return new SearchCriteriaResponse(
                criteria.filters().stream()
                        .map(filter -> new SearchFilterResponse(
                                filter.field(), filter.operator().value(), filter.value()))
                        .toList(),
                order == null ? null : order.field(),
                order == null ? null : order.direction().name(),
                criteria.limit(),
                criteria.offset());
    }

    public Criteria toCriteria(ProductSearchRequest request) {
        if (request == null) {
            throw invalid("Search request is required.");
        }
        if (request.message() != null) {
            if (request.message().isBlank() || request.filters() != null || request.orderBy() != null
                    || request.order() != null || request.limit() != null || request.offset() != null) {
                throw invalid("Use either a message or structured search criteria.");
            }
            return null;
        }

        List<Filter> filters = new ArrayList<>();
        if (request.filters() != null) {
            for (SearchFilterRequest filter : request.filters()) {
                if (filter == null) {
                    throw invalid("Null filters are not allowed.");
                }
                FilterOperator operator;
                try {
                    operator = FilterOperator.fromValue(filter.operator());
                } catch (IllegalArgumentException exception) {
                    throw new ProductSearchException("SEARCH_OPERATOR_NOT_ALLOWED", "Filter operator is not recognized.");
                }
                filters.add(new Filter(filter.field(), operator, value(filter.value())));
            }
        }

        Order order = null;
        if (request.orderBy() != null) {
            SortDirection direction;
            try {
                direction = request.order() == null ? SortDirection.ASC : SortDirection.fromValue(request.order());
            } catch (IllegalArgumentException exception) {
                throw new ProductSearchException("SEARCH_ORDER_NOT_ALLOWED", "Sort direction is invalid.");
            }
            order = new Order(request.orderBy(), direction);
        } else if (request.order() != null) {
            throw new ProductSearchException("SEARCH_ORDER_NOT_ALLOWED", "order_by is required when order is set.");
        }
        return new Criteria(filters, order, request.limit(), request.offset());
    }

    private Object value(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.textValue();
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        if (node.isArray()) {
            List<Object> values = new ArrayList<>();
            node.forEach(item -> values.add(value(item)));
            return values;
        }
        throw new ProductSearchException("SEARCH_VALUE_INVALID", "Filter value must be a scalar or array.");
    }

    private ProductSearchException invalid(String message) {
        return new ProductSearchException("INVALID_SEARCH_REQUEST", message);
    }
}
