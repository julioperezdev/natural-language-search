package dev.julioperez.nls.conversation.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ConversationRepository {
    Conversation lockOrCreate(ConversationIdentity identity);

    Optional<String> findResponseByProviderMessageId(UUID conversationId, String providerMessageId);

    void recordProcessedMessage(
            UUID conversationId,
            String providerMessageId,
            String responseJson,
            Instant processedAt);

    long countMessages(UUID conversationId);

    UUID append(ConversationMessage message);

    void updateSearchState(UUID conversationId, String stateJson, long nextMessageSequence, Instant updatedAt);

    void retainLatestMessages(UUID conversationId, long nextMessageSequence, int maximumMessages);
}
