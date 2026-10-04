package dev.julioperez.nls.products.infrastructure.repository.postgres;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductBaseJpaRepository extends JpaRepository<ProductJpaEntity, UUID> {
    @Query("""
            select new dev.julioperez.nls.products.infrastructure.repository.postgres.ProductSearchDetailRow(
                p.id, p.name, c.name
            )
            from ProductJpaEntity p
            join p.category c
            where p.id in :ids
            order by p.id
            """)
    List<ProductSearchDetailRow> findSearchDetailsByIdIn(@Param("ids") Collection<UUID> ids);
}
