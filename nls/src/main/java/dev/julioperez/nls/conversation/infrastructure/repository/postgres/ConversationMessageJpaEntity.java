package dev.julioperez.nls.conversation.infrastructure.repository.postgres;

import dev.julioperez.nls.conversation.domain.ConversationMessage;
import dev.julioperez.nls.conversation.domain.ConversationMessageDirection;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "search_conversation_messages")
public class ConversationMessageJpaEntity {
    @Id
    private UUID id;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(name = "message_sequence", nullable = false)
    private long sequence;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 16)
    private ConversationMessageDirection direction;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "provider_message_id", length = 200)
    private String providerMessageId;

    @Column(name = "reply_to_message_id")
    private UUID replyToMessageId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ConversationMessageJpaEntity() {
    }

    public ConversationMessageJpaEntity(ConversationMessage message) {
        this.id = message.id();
        this.conversationId = message.conversationId();
        this.sequence = message.sequence();
        this.direction = message.direction();
        this.content = message.content();
        this.providerMessageId = message.providerMessageId();
        this.replyToMessageId = message.replyToMessageId();
        this.createdAt = message.createdAt();
    }

    public UUID getId() {
        return id;
    }

}
