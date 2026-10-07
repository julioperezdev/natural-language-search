package dev.julioperez.nls.conversation.domain;

public record ConversationIdentity(
        ConversationChannel channel,
        String channelAccountId,
        String participantId) {
    public ConversationIdentity {
        if (channel == null || channelAccountId == null || participantId == null
                || channelAccountId.isBlank() || participantId.isBlank()
                || channelAccountId.length() > 160 || participantId.length() > 160) {
            throw new IllegalArgumentException("Conversation identity is invalid.");
        }
        channelAccountId = channelAccountId.strip();
        participantId = participantId.strip();
    }
}
