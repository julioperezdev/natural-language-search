package dev.julioperez.nls.conversation.infrastructure.repository.postgres;

import dev.julioperez.nls.conversation.domain.Conversation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "search_conversations")
public class ConversationJpaEntity {
    @Id
    private UUID id;

    @Column(nullable = false, length = 32)
    private String channel;

    @Column(name = "identity_digest", nullable = false, length = 64)
    private String identityDigest;

    @Column(name = "search_state_json", columnDefinition = "TEXT")
    private String searchStateJson;

    @Column(name = "next_message_sequence", nullable = false)
    private long nextMessageSequence;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected ConversationJpaEntity() {
    }

    public Conversation toDomain() {
        return new Conversation(id, searchStateJson, nextMessageSequence, createdAt, updatedAt);
    }

    public void updateSearchState(String stateJson, long nextSequence, Instant timestamp) {
        this.searchStateJson = stateJson;
        this.nextMessageSequence = nextSequence;
        this.updatedAt = timestamp;
    }
}
