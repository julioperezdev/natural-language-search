package dev.julioperez.nls.evaluation.infrastructure.repository;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvaluationRunJpaRepository extends JpaRepository<EvaluationRunJpaEntity, UUID> {
    Page<EvaluationRunJpaEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
