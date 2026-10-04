package dev.julioperez.nls.products.infrastructure.repository.postgres;

import dev.julioperez.nls.products.domain.search.Criteria;
import dev.julioperez.nls.products.domain.search.ProductSearchItem;
import dev.julioperez.nls.products.domain.search.ProductSearchPage;
import dev.julioperez.nls.products.domain.search.ProductSearchRepository;
import dev.julioperez.nls.products.domain.search.ProductVariantSummary;
import dev.julioperez.nls.products.domain.search.SearchSchema;
import dev.julioperez.nls.products.infrastructure.search.HibernateCriteriaConverter;
import dev.julioperez.nls.products.infrastructure.search.ProductSearchFieldRegistry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class ProductJpaRepository implements ProductSearchRepository {
    private final ProductBaseJpaRepository jpa;
    private final HibernateCriteriaConverter criteriaConverter;
    private final ProductSearchFieldRegistry fields;

    public ProductJpaRepository(
            ProductBaseJpaRepository jpa,
            HibernateCriteriaConverter criteriaConverter,
            ProductSearchFieldRegistry fields) {
        this.jpa = jpa;
        this.criteriaConverter = criteriaConverter;
        this.fields = fields;
    }

    @Override
    @Transactional(readOnly = true)
    public ProductSearchPage search(Criteria criteria) {
        Criteria validated = validate(criteria);
        boolean distinct = fields.requiresDistinct(validated);
        List<ProductJpaEntity> products = (distinct
                ? criteriaConverter.convertDistinct(validated, fields)
                : criteriaConverter.convert(validated, fields))
                .getResultList();
        long total = (distinct
                ? criteriaConverter.convertToDistinctCount(validated, fields)
                : criteriaConverter.convertToCount(validated, fields))
                .getSingleResult();

        List<UUID> productIds = products.stream().map(ProductJpaEntity::getId).toList();
        Map<UUID, ProductDetails> detailsById = new LinkedHashMap<>();
        if (!productIds.isEmpty()) {
            for (ProductSearchDetailRow row : jpa.findSearchDetailsByIdIn(productIds)) {
                detailsById.put(row.productId(), new ProductDetails(row.productName(), row.category()));
            }
        }
        Map<UUID, List<ProductVariantSummary>> matchingVariantsByProduct = new LinkedHashMap<>();
        for (ProductSearchVariantRow row : criteriaConverter.matchingVariants(validated, fields, productIds)) {
            matchingVariantsByProduct.computeIfAbsent(row.productId(), ignored -> new ArrayList<>())
                    .add(new ProductVariantSummary(
                            row.variantId(), row.color(), row.size(), row.price(), row.stock()));
        }
        List<ProductSearchItem> items = products.stream()
                .map(product -> {
                    ProductDetails details = detailsById.get(product.getId());
                    return new ProductSearchItem(
                            product.getId(), details.name(), details.category(),
                            matchingVariantsByProduct.getOrDefault(product.getId(), List.of()));
                })
                .toList();
        return new ProductSearchPage(items, total, validated.limit(), validated.offset());
    }

    @Override
    @Transactional(readOnly = true)
    public Criteria validate(Criteria criteria) {
        return fields.validate(criteria);
    }

    @Override
    @Transactional(readOnly = true)
    public SearchSchema schema() {
        return fields.schema();
    }

    private record ProductDetails(String name, String category) {
    }
}
