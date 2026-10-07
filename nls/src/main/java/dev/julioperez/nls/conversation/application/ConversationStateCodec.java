package dev.julioperez.nls.conversation.application;

import dev.julioperez.nls.products.domain.search.Criteria;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class ConversationStateCodec {
    private final ObjectMapper objectMapper;

    public ConversationStateCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Criteria decodeCriteria(String json, int defaultLimit) {
        if (json == null || json.isBlank()) {
            return emptyCriteria(defaultLimit);
        }
        try {
            return objectMapper.readValue(json, Criteria.class);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Stored conversation search state is invalid.", exception);
        }
    }

    public String encodeCriteria(Criteria criteria) {
        try {
            return objectMapper.writeValueAsString(criteria);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Conversation search state could not be stored.", exception);
        }
    }

    public String encodeResult(ConversationMessageResult result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Conversation response could not be stored.", exception);
        }
    }

    public ConversationMessageResult decodeResult(String json) {
        try {
            return objectMapper.readValue(json, ConversationMessageResult.class);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Stored conversation response is invalid.", exception);
        }
    }

    private Criteria emptyCriteria(int defaultLimit) {
        return new Criteria(List.of(), null, defaultLimit, 0);
    }
}
