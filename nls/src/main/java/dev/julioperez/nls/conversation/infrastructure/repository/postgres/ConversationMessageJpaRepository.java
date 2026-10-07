package dev.julioperez.nls.conversation.infrastructure.repository.postgres;

import dev.julioperez.nls.conversation.domain.ConversationMessageDirection;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

public interface ConversationMessageJpaRepository
        extends JpaRepository<ConversationMessageJpaEntity, UUID> {
    long countByConversationId(UUID conversationId);

    @Modifying
    int deleteByConversationIdAndSequenceLessThanAndDirection(
            UUID conversationId,
            long sequence,
            ConversationMessageDirection direction);
}
