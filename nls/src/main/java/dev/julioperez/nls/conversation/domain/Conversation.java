package dev.julioperez.nls.conversation.domain;

import java.time.Instant;
import java.util.UUID;

public record Conversation(
        UUID id,
        String searchStateJson,
        long nextMessageSequence,
        Instant createdAt,
        Instant updatedAt) {
}
