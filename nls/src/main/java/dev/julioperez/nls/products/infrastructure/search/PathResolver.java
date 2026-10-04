package dev.julioperez.nls.products.infrastructure.search;

import dev.julioperez.nls.products.infrastructure.repository.postgres.ProductJpaEntity;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;

@FunctionalInterface
public interface PathResolver {
    Path<?> resolve(Root<ProductJpaEntity> root, JoinRegistry joins);
}
