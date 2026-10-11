package dev.julioperez.nls.conversation.infrastructure.repository.postgres;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationJpaRepository extends JpaRepository<ConversationJpaEntity, UUID> {
    long countByChannel(String channel);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO search_conversations
                (id, channel, identity_digest, search_state_json, next_message_sequence, created_at, updated_at, version)
            VALUES
                (:id, :channel, :identityDigest, NULL, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
            ON CONFLICT (channel, identity_digest) DO NOTHING
            """, nativeQuery = true)
    int createIfAbsent(
            @Param("id") UUID id,
            @Param("channel") String channel,
            @Param("identityDigest") String identityDigest);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select conversation from ConversationJpaEntity conversation
            where conversation.channel = :channel and conversation.identityDigest = :identityDigest
            """)
    Optional<ConversationJpaEntity> findForUpdate(
            @Param("channel") String channel,
            @Param("identityDigest") String identityDigest);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select conversation from ConversationJpaEntity conversation where conversation.id = :id")
    Optional<ConversationJpaEntity> findForUpdateById(@Param("id") UUID id);

    @Modifying(flushAutomatically = true)
    @Query("delete from ConversationJpaEntity conversation "
            + "where conversation.channel = :channel and conversation.identityDigest = :identityDigest")
    int deleteByIdentity(
            @Param("channel") String channel,
            @Param("identityDigest") String identityDigest);
}
