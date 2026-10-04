package dev.julioperez.nls.products.infrastructure.repository.postgres;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ProductVariantBaseJpaRepository extends JpaRepository<ProductVariantJpaEntity, UUID> {
    @Query("select distinct variant.color from ProductVariantJpaEntity variant order by variant.color")
    List<String> findSearchColors();

    @Query("select distinct variant.size from ProductVariantJpaEntity variant order by variant.size")
    List<String> findSearchSizes();
}
