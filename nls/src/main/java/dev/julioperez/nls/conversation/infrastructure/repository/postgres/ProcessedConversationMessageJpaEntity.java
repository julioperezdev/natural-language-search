package dev.julioperez.nls.conversation.infrastructure.repository.postgres;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "processed_conversation_messages")
public class ProcessedConversationMessageJpaEntity {
    @Id
    private UUID id;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(name = "provider_message_id", nullable = false, length = 200)
    private String providerMessageId;

    @Column(name = "response_json", nullable = false, columnDefinition = "TEXT")
    private String responseJson;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected ProcessedConversationMessageJpaEntity() {
    }

    public ProcessedConversationMessageJpaEntity(
            UUID conversationId,
            String providerMessageId,
            String responseJson,
            Instant processedAt) {
        this.id = UUID.randomUUID();
        this.conversationId = conversationId;
        this.providerMessageId = providerMessageId;
        this.responseJson = responseJson;
        this.processedAt = processedAt;
    }

    public String getResponseJson() {
        return responseJson;
    }
}
