package dev.julioperez.nls.products.infrastructure.repository.postgres;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CategoryBaseJpaRepository extends JpaRepository<CategoryJpaEntity, UUID> {
    @Query("select category.name from CategoryJpaEntity category order by category.name")
    List<String> findSearchValues();
}
