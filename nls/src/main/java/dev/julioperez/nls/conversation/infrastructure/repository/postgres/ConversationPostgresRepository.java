package dev.julioperez.nls.conversation.infrastructure.repository.postgres;

import dev.julioperez.nls.conversation.domain.Conversation;
import dev.julioperez.nls.conversation.domain.ConversationIdentity;
import dev.julioperez.nls.conversation.domain.ConversationMessage;
import dev.julioperez.nls.conversation.domain.ConversationMessageDirection;
import dev.julioperez.nls.conversation.domain.ConversationRepository;
import dev.julioperez.nls.conversation.infrastructure.security.ConversationIdentityHasher;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
    @Transactional(readOnly = true)
    public List<ConversationMessage> latestMessages(UUID conversationId, int maximumMessages) {
        int boundedMaximum = Math.max(0, Math.min(maximumMessages, 20));
        List<ConversationMessageJpaEntity> latest = messages
                .findTop20ByConversationIdOrderBySequenceDesc(conversationId);
        return chronological(latest, boundedMaximum);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConversationMessage> latestMessagesAfterSequence(
            UUID conversationId,
            long sequenceExclusive,
            int maximumMessages) {
        int boundedMaximum = Math.max(0, Math.min(maximumMessages, 20));
        List<ConversationMessageJpaEntity> latest = messages
                .findTop20ByConversationIdAndSequenceGreaterThanOrderBySequenceDesc(conversationId, sequenceExclusive);
        return chronological(latest, boundedMaximum);
    }

    private static List<ConversationMessage> chronological(
            List<ConversationMessageJpaEntity> latest,
            int maximumMessages) {
        List<ConversationMessage> ordered = new ArrayList<>(latest.stream()
                .limit(maximumMessages)
                .map(ConversationMessageJpaEntity::toDomain)
                .toList());
        ordered.sort(Comparator.comparingLong(ConversationMessage::sequence));
        return List.copyOf(ordered);
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

    @Override
    @Transactional
    public void deleteByIdentity(ConversationIdentity identity) {
        conversations.deleteByIdentity(identity.channel().name(), identityHasher.hash(identity));
    }
}
