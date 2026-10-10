package dev.julioperez.nls.products.application;

import dev.julioperez.nls.products.domain.search.Criteria;
import dev.julioperez.nls.products.domain.search.Filter;
import dev.julioperez.nls.products.domain.search.SearchSchema;
import java.util.ArrayList;
import java.util.List;

public interface SearchDecisionEngine {
    Criteria interpret(String message, SearchSchema schema);

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
