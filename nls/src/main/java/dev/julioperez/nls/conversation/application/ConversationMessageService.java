package dev.julioperez.nls.conversation.application;

import dev.julioperez.nls.conversation.domain.Conversation;
import dev.julioperez.nls.conversation.domain.ConversationMessage;
import dev.julioperez.nls.conversation.domain.ConversationMessageDirection;
import dev.julioperez.nls.conversation.domain.ConversationRepository;
import dev.julioperez.nls.products.domain.search.Criteria;
import dev.julioperez.nls.productsearch.application.ProductSearchAnswer;
import dev.julioperez.nls.productsearch.application.ProductSearchConversationService;
import dev.julioperez.nls.infrastructure.logging.RequestLogContext;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConversationMessageService {
    public static final int MAX_CONTEXT_MESSAGES = 20;
    private static final Logger log = LoggerFactory.getLogger(ConversationMessageService.class);
    private final ConversationRepository conversations;
    private final ConversationStateCodec stateCodec;
    private final ProductSearchConversationService productSearch;

    public ConversationMessageService(
            ConversationRepository conversations,
            ConversationStateCodec stateCodec,
            ProductSearchConversationService productSearch) {
        this.conversations = conversations;
        this.stateCodec = stateCodec;
        this.productSearch = productSearch;
    }

    @Transactional
    public ConversationMessageResult handle(ConversationMessageCommand command) {
        String requestId = RequestLogContext.requestId();
        Conversation conversation = conversations.lockOrCreate(command.identity());
        var previousResponse = conversations.findResponseByProviderMessageId(
                conversation.id(), command.providerMessageId());
        if (previousResponse.isPresent()) {
            log.info("CONVERSATION_MESSAGE_REPLAYED requestId={} result=deduplicated", requestId);
            return stateCodec.decodeResult(previousResponse.get());
        }

        Criteria currentCriteria = stateCodec.decodeCriteria(conversation.searchStateJson(), 10);
        long searchStartedAt = System.nanoTime();
        log.info("CONVERSATION_PRODUCT_SEARCH_STARTED requestId={}", requestId);
        ProductSearchAnswer answer;
        try {
            answer = productSearch.answer(command.message(), currentCriteria);
        } catch (RuntimeException exception) {
            log.error("CONVERSATION_PRODUCT_SEARCH_FAILED requestId={} errorType={} durationMs={}",
                    requestId, exception.getClass().getSimpleName(), elapsedMillis(searchStartedAt));
            throw exception;
        }
        long totalResults = answer.results() == null ? 0 : answer.results().total();
        log.info("CONVERSATION_PRODUCT_SEARCH_COMPLETED requestId={} outcome={} totalResults={} durationMs={}",
                requestId, answer.outcome(), totalResults, elapsedMillis(searchStartedAt));
        Criteria nextCriteria = answer.criteria();
        long inboundSequence = conversation.nextMessageSequence() + 1;
        Instant now = Instant.now();

        UUID inboundId = conversations.append(new ConversationMessage(
                UUID.randomUUID(), conversation.id(), inboundSequence, ConversationMessageDirection.INBOUND,
                command.message(), command.providerMessageId(), null, now));
        int retainedAfterInbound = (int) Math.min(
                MAX_CONTEXT_MESSAGES, conversations.countMessages(conversation.id()));
        int retainedAfterReply = Math.min(MAX_CONTEXT_MESSAGES, retainedAfterInbound + 1);
        ConversationMessageResult result = new ConversationMessageResult(
                conversation.id(), ConversationMessageOutcome.from(answer.outcome()), answer.reply(),
                answer.results(), nextCriteria,
                retainedAfterReply, MAX_CONTEXT_MESSAGES);
        String responseJson = stateCodec.encodeResult(result);
        conversations.recordProcessedMessage(
                conversation.id(), command.providerMessageId(), responseJson, now);
        conversations.append(new ConversationMessage(
                UUID.randomUUID(), conversation.id(), inboundSequence + 1, ConversationMessageDirection.OUTBOUND,
                answer.reply(), null, inboundId, now));
        conversations.updateSearchState(
                conversation.id(), stateCodec.encodeCriteria(nextCriteria), inboundSequence + 1, now);
        conversations.retainLatestMessages(
                conversation.id(), inboundSequence + 1, MAX_CONTEXT_MESSAGES);
        log.info("CONVERSATION_STATE_WRITE_STAGED requestId={} retainedMessages={}",
                requestId, retainedAfterReply);
        return result;
    }

    @Transactional
    public ConversationMessageResult resetContext(ConversationMessageCommand command) {
        String requestId = RequestLogContext.requestId();
        Conversation conversation = conversations.lockOrCreate(command.identity());
        var previousResponse = conversations.findResponseByProviderMessageId(
                conversation.id(), command.providerMessageId());
        if (previousResponse.isPresent()) {
            log.info("CONVERSATION_MESSAGE_REPLAYED requestId={} result=deduplicated", requestId);
            return stateCodec.decodeResult(previousResponse.get());
        }

        Criteria clearedCriteria = stateCodec.emptyCriteria(10);
        long inboundSequence = conversation.nextMessageSequence() + 1;
        Instant now = Instant.now();
        UUID inboundId = conversations.append(new ConversationMessage(
                UUID.randomUUID(), conversation.id(), inboundSequence, ConversationMessageDirection.INBOUND,
                command.message(), command.providerMessageId(), null, now));
        int retainedAfterInbound = (int) Math.min(
                MAX_CONTEXT_MESSAGES, conversations.countMessages(conversation.id()));
        int retainedAfterReply = Math.min(MAX_CONTEXT_MESSAGES, retainedAfterInbound + 1);
        ConversationMessageResult result = new ConversationMessageResult(
                conversation.id(), ConversationMessageOutcome.CONTEXT_RESET,
                "Listo, reinicié el contexto de búsqueda. ¿Qué producto estás buscando?",
                null, clearedCriteria, retainedAfterReply, MAX_CONTEXT_MESSAGES);
        conversations.recordProcessedMessage(conversation.id(), command.providerMessageId(),
                stateCodec.encodeResult(result), now);
        conversations.append(new ConversationMessage(
                UUID.randomUUID(), conversation.id(), inboundSequence + 1, ConversationMessageDirection.OUTBOUND,
                result.reply(), null, inboundId, now));
        conversations.updateSearchState(
                conversation.id(), stateCodec.encodeCriteria(clearedCriteria), inboundSequence + 1, now);
        conversations.retainLatestMessages(
                conversation.id(), inboundSequence + 1, MAX_CONTEXT_MESSAGES);
        log.info("CONVERSATION_CONTEXT_RESET requestId={} retainedMessages={}",
                requestId, retainedAfterReply);
        return result;
    }

    private static long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
