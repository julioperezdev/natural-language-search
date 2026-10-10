package dev.julioperez.nls.products.infrastructure.ai.typesafe;

import dev.julioperez.nls.products.domain.search.Criteria;
import dev.julioperez.nls.products.application.SearchInterpretationFailedException;
import java.util.List;

record TypeSafeInterpretationEvaluation(
        Criteria criteria,
        String method,
        SearchInterpretationFailedException.Reason failureReason,
        String failureField,
        String providerChoices,
        String providerModel,
        List<TypeSafeChoiceEvaluation> providerDecisions,
        Integer inputTokens,
        Integer outputTokens,
        long durationMillis) {

    TypeSafeInterpretationEvaluation {
        providerDecisions = providerDecisions == null ? List.of() : List.copyOf(providerDecisions);
    }
}
