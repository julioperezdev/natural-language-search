package dev.julioperez.nls.conversation.infrastructure.repository.postgres;

import dev.julioperez.nls.conversation.domain.ConversationMessageDirection;
import java.util.UUID;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

public interface ConversationMessageJpaRepository
        extends JpaRepository<ConversationMessageJpaEntity, UUID> {
    long countByConversationId(UUID conversationId);

    List<ConversationMessageJpaEntity> findTop20ByConversationIdOrderBySequenceDesc(UUID conversationId);

    List<ConversationMessageJpaEntity> findTop20ByConversationIdAndSequenceGreaterThanOrderBySequenceDesc(
            UUID conversationId,
            long sequenceExclusive);

    @Modifying
    int deleteByConversationIdAndSequenceLessThanAndDirection(
            UUID conversationId,
            long sequence,
            ConversationMessageDirection direction);
}
