package dev.julioperez.nls.products.infrastructure.search;

import dev.julioperez.nls.products.domain.search.Criteria;
import dev.julioperez.nls.products.domain.search.Filter;
import dev.julioperez.nls.products.domain.search.FilterOperator;
import dev.julioperez.nls.products.domain.search.Order;
import dev.julioperez.nls.products.domain.search.ProductSearchException;
import dev.julioperez.nls.products.domain.search.SortDirection;
import dev.julioperez.nls.products.infrastructure.repository.postgres.ProductJpaEntity;
import dev.julioperez.nls.products.infrastructure.repository.postgres.ProductSearchVariantRow;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Selection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class HibernateCriteriaConverter {
    private final EntityManager entityManager;

    public HibernateCriteriaConverter(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public TypedQuery<ProductJpaEntity> convert(
            Criteria criteria,
            ProductSearchFieldRegistry fields) {
        return resultQuery(criteria, fields, false);
    }

    public TypedQuery<ProductJpaEntity> convertDistinct(
            Criteria criteria,
            ProductSearchFieldRegistry fields) {
        return resultQuery(criteria, fields, true);
    }

    public TypedQuery<Long> convertToCount(
            Criteria criteria,
            ProductSearchFieldRegistry fields) {
        return countQuery(criteria, fields, false);
    }

    public TypedQuery<Long> convertToDistinctCount(
            Criteria criteria,
            ProductSearchFieldRegistry fields) {
        return countQuery(criteria, fields, true);
    }

    public List<ProductSearchVariantRow> matchingVariants(
            Criteria criteria,
            ProductSearchFieldRegistry fields,
            Collection<UUID> productIds) {
        if (productIds.isEmpty()) {
            return List.of();
        }
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<ProductSearchVariantRow> query = builder.createQuery(ProductSearchVariantRow.class);
        Root<ProductJpaEntity> root = query.from(ProductJpaEntity.class);
        JoinRegistry joins = new JoinRegistry(root);
        List<Predicate> conditions = new ArrayList<>(predicates(criteria, fields, builder, root, joins));
        conditions.add(root.get("id").in(productIds));

        Join<?, ?> variant = joins.join("variants");
        query.select(builder.construct(
                ProductSearchVariantRow.class,
                root.get("id"),
                variant.get("id"),
                variant.get("color"),
                variant.get("size"),
                variant.get("price"),
                variant.get("stock")));
        query.where(builder.and(conditions.toArray(Predicate[]::new)));
        query.orderBy(
                builder.asc(root.get("id")),
                builder.asc(variant.get("price")),
                builder.asc(variant.get("color")),
                builder.asc(variant.get("size")),
                builder.asc(variant.get("id")));
        return entityManager.createQuery(query).getResultList();
    }

    private TypedQuery<ProductJpaEntity> resultQuery(
            Criteria criteria,
            ProductSearchFieldRegistry fields,
            boolean distinct) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<ProductJpaEntity> query = builder.createQuery(ProductJpaEntity.class);
        Root<ProductJpaEntity> root = query.from(ProductJpaEntity.class);
        JoinRegistry joins = new JoinRegistry(root);
        List<Predicate> predicates = predicates(criteria, fields, builder, root, joins);

        query.select(root).distinct(distinct);
        if (!predicates.isEmpty()) {
            query.where(builder.and(predicates.toArray(Predicate[]::new)));
        }
        applyOrdering(criteria.order(), fields, builder, query, root, joins);

        return entityManager.createQuery(query)
                .setFirstResult(criteria.offset())
                .setMaxResults(criteria.limit());
    }

    private TypedQuery<Long> countQuery(
            Criteria criteria,
            ProductSearchFieldRegistry fields,
            boolean distinct) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> query = builder.createQuery(Long.class);
        Root<ProductJpaEntity> root = query.from(ProductJpaEntity.class);
        JoinRegistry joins = new JoinRegistry(root);
        List<Predicate> predicates = predicates(criteria, fields, builder, root, joins);

        Expression<Long> count = distinct ? builder.countDistinct(root) : builder.count(root);
        query.select(count);
        if (!predicates.isEmpty()) {
            query.where(builder.and(predicates.toArray(Predicate[]::new)));
        }
        return entityManager.createQuery(query);
    }

    private <T> List<Predicate> predicates(
            Criteria criteria,
            ProductSearchFieldRegistry fields,
            CriteriaBuilder builder,
            Root<ProductJpaEntity> root,
            JoinRegistry joins) {
        return criteria.filters().stream()
                .map(filter -> predicate(filter, fields, builder, root, joins))
                .toList();
    }

    private <T> Predicate predicate(
            Filter filter,
            ProductSearchFieldRegistry fields,
            CriteriaBuilder builder,
            Root<ProductJpaEntity> root,
            JoinRegistry joins) {
        SearchFieldDefinition field = fields.get(filter.field());
        Path<?> path = fields.resolve(field, root, joins);
        Object value = filter.value();

        return switch (filter.operator()) {
            case EQUALS -> equal(builder, path, value);
            case NOT_EQUALS -> builder.not(equal(builder, path, value));
            case GREATER_THAN, LESS_THAN, LESS_THAN_OR_EQUAL, GREATER_THAN_OR_EQUAL ->
                    compare(builder, path, value, filter.operator());
            case CONTAINS -> textMatch(builder, path, (String) value, false);
            case NOT_CONTAINS -> textMatch(builder, path, (String) value, true);
            case IN -> path.in((Collection<?>) value);
            case NOT_IN -> builder.not(path.in((Collection<?>) value));
            case INCLUDES -> listMatch(builder, path, (Collection<?>) value, false);
            case INCLUDES_OR -> listMatch(builder, path, (Collection<?>) value, true);
        };
    }

    private <T> void applyOrdering(
            Order order,
            ProductSearchFieldRegistry fields,
            CriteriaBuilder builder,
            CriteriaQuery<ProductJpaEntity> query,
            Root<ProductJpaEntity> root,
            JoinRegistry joins) {
        if (order == null) {
            query.orderBy(builder.asc(root.get("id")));
            return;
        }

        Path<?> path = fields.resolve(fields.get(order.field()), root, joins);
        jakarta.persistence.criteria.Order primary = order.direction() == SortDirection.ASC
                ? builder.asc(path)
                : builder.desc(path);
        if ("id".equals(order.field())) {
            query.orderBy(primary);
            return;
        }
        query.orderBy(primary, builder.asc(root.get("id")));
    }

    private Predicate equal(CriteriaBuilder builder, Path<?> path, Object value) {
        if (path.getJavaType() == String.class && value instanceof String string) {
            return builder.equal(builder.lower(path.as(String.class)), string.toLowerCase());
        }
        return builder.equal(path, value);
    }

    private Predicate compare(CriteriaBuilder builder, Path<?> path, Object value, FilterOperator operator) {
        if (!(value instanceof Comparable<?> comparable)) {
            throw new ProductSearchException("SEARCH_VALUE_INVALID", "Comparison value is not comparable.");
        }
        @SuppressWarnings({"rawtypes", "unchecked"})
        Expression<? extends Comparable> expression = (Expression) path;
        @SuppressWarnings({"rawtypes", "unchecked"})
        Comparable comparableValue = (Comparable) comparable;
        return switch (operator) {
            case GREATER_THAN -> builder.greaterThan(expression, comparableValue);
            case LESS_THAN -> builder.lessThan(expression, comparableValue);
            case LESS_THAN_OR_EQUAL -> builder.lessThanOrEqualTo(expression, comparableValue);
            case GREATER_THAN_OR_EQUAL -> builder.greaterThanOrEqualTo(expression, comparableValue);
            default -> throw new IllegalArgumentException("Unsupported comparison operator.");
        };
    }

    private Predicate textMatch(CriteriaBuilder builder, Path<?> path, String value, boolean negate) {
        String escaped = value.toLowerCase()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        Expression<String> expression = builder.lower(path.as(String.class));
        String pattern = "%" + escaped + "%";
        return negate
                ? builder.notLike(expression, pattern, '\\')
                : builder.like(expression, pattern, '\\');
    }

    private Predicate listMatch(
            CriteriaBuilder builder,
            Path<?> path,
            Collection<?> values,
            boolean anyValue) {
        Expression<String> delimited = builder.concat(
                builder.concat(",", builder.lower(path.as(String.class))), ",");
        List<Predicate> matches = values.stream()
                .map(value -> {
                    String escaped = value.toString().toLowerCase()
                            .replace("\\", "\\\\")
                            .replace("%", "\\%")
                            .replace("_", "\\_");
                    return builder.like(delimited, ",%" + escaped + ",%", '\\');
                })
                .toList();
        return anyValue
                ? builder.or(matches.toArray(Predicate[]::new))
                : builder.and(matches.toArray(Predicate[]::new));
    }

}
