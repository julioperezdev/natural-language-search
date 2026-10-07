package dev.julioperez.nls.conversation.infrastructure.repository.postgres;

import dev.julioperez.nls.conversation.domain.Conversation;
import dev.julioperez.nls.conversation.domain.ConversationIdentity;
import dev.julioperez.nls.conversation.domain.ConversationMessage;
import dev.julioperez.nls.conversation.domain.ConversationMessageDirection;
import dev.julioperez.nls.conversation.domain.ConversationRepository;
import dev.julioperez.nls.conversation.infrastructure.security.ConversationIdentityHasher;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class ConversationPostgresRepository implements ConversationRepository {
    private final ConversationJpaRepository conversations;
    private final ConversationMessageJpaRepository messages;
    private final ProcessedConversationMessageJpaRepository processedMessages;
    private final ConversationIdentityHasher identityHasher;

    public ConversationPostgresRepository(
            ConversationJpaRepository conversations,
            ConversationMessageJpaRepository messages,
            ProcessedConversationMessageJpaRepository processedMessages,
            ConversationIdentityHasher identityHasher) {
        this.conversations = conversations;
        this.messages = messages;
        this.processedMessages = processedMessages;
        this.identityHasher = identityHasher;
    }

    @Override
    @Transactional
    public Conversation lockOrCreate(ConversationIdentity identity) {
        String digest = identityHasher.hash(identity);
        conversations.createIfAbsent(UUID.randomUUID(), identity.channel().name(), digest);
        return conversations.findForUpdate(identity.channel().name(), digest)
                .orElseThrow(() -> new IllegalStateException("Conversation could not be resolved."))
                .toDomain();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> findResponseByProviderMessageId(UUID conversationId, String providerMessageId) {
        return processedMessages.findByConversationIdAndProviderMessageId(conversationId, providerMessageId)
                .map(ProcessedConversationMessageJpaEntity::getResponseJson);
    }

    @Override
    @Transactional
    public void recordProcessedMessage(
            UUID conversationId,
            String providerMessageId,
            String responseJson,
            Instant processedAt) {
        processedMessages.saveAndFlush(new ProcessedConversationMessageJpaEntity(
                conversationId, providerMessageId, responseJson, processedAt));
    }

    @Override
    @Transactional(readOnly = true)
    public long countMessages(UUID conversationId) {
        return messages.countByConversationId(conversationId);
    }

    @Override
    @Transactional
    public UUID append(ConversationMessage message) {
        return messages.saveAndFlush(new ConversationMessageJpaEntity(message)).getId();
    }

    @Override
    @Transactional
    public void updateSearchState(UUID conversationId, String stateJson, long nextMessageSequence, Instant updatedAt) {
        ConversationJpaEntity conversation = conversations.findForUpdateById(conversationId)
                .orElseThrow(() -> new IllegalStateException("Conversation could not be updated."));
        conversation.updateSearchState(stateJson, nextMessageSequence, updatedAt);
    }

    @Override
    @Transactional
    public void retainLatestMessages(UUID conversationId, long nextMessageSequence, int maximumMessages) {
        long firstRetainedSequence = Math.max(1, nextMessageSequence - maximumMessages + 1);
        messages.deleteByConversationIdAndSequenceLessThanAndDirection(
                conversationId, firstRetainedSequence, ConversationMessageDirection.OUTBOUND);
        messages.deleteByConversationIdAndSequenceLessThanAndDirection(
                conversationId, firstRetainedSequence, ConversationMessageDirection.INBOUND);
    }
}
