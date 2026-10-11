package dev.julioperez.nls.products.application;

import dev.julioperez.nls.products.domain.search.Criteria;
import dev.julioperez.nls.products.domain.search.Filter;
import dev.julioperez.nls.products.domain.search.SearchSchema;
import java.util.ArrayList;
import java.util.List;

public interface SearchDecisionEngine {
    Criteria interpret(String message, SearchSchema schema);

    default SearchInterpretationResult interpretTurnWithTelemetry(
            String message,
            SearchSchema schema,
            Criteria current,
            SearchConversationContext context) {
        long startedAt = System.nanoTime();
        Criteria criteria = interpretTurn(message, schema, current, context);
        return new SearchInterpretationResult(criteria, new SearchInterpretationTelemetry(
                "decision_engine", "unknown", false, null, null,
                (System.nanoTime() - startedAt) / 1_000_000));
    }

    /**
     * Interprets one conversational turn and applies its filters to the current search state.
     * Provider adapters can override this method when they support explicit keep/clear decisions.
     */
    default Criteria interpretTurn(String message, SearchSchema schema, Criteria current) {
        Criteria previous = current == null ? new Criteria(List.of(), null, null, 0) : current;
        Criteria delta = interpret(message, schema);
        List<Filter> filters = new ArrayList<>(previous.filters());
        for (Filter filter : delta.filters()) {
            filters.removeIf(existing -> existing.field().equals(filter.field()));
            filters.add(filter);
        }
        return new Criteria(
                filters,
                delta.order() == null ? previous.order() : delta.order(),
                delta.limit() == null ? previous.limit() : delta.limit(),
                0);
    }

    /** Interprets a turn with prior messages and validated search snapshots available for references. */
    default Criteria interpretTurn(
            String message,
            SearchSchema schema,
            Criteria current,
            SearchConversationContext context) {
        return interpretTurn(message, schema, current);
    }
}
