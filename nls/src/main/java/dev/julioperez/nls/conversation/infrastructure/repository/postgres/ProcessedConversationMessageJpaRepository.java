package dev.julioperez.nls.conversation.infrastructure.repository.postgres;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedConversationMessageJpaRepository
        extends JpaRepository<ProcessedConversationMessageJpaEntity, UUID> {
    Optional<ProcessedConversationMessageJpaEntity> findByConversationIdAndProviderMessageId(
            UUID conversationId,
            String providerMessageId);
}
