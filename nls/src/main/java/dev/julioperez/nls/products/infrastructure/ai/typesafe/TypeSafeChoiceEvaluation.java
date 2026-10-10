package dev.julioperez.nls.products.infrastructure.ai.typesafe;

import java.util.Map;

/** A provider choice captured for offline evaluation, including its complete probability distribution. */
record TypeSafeChoiceEvaluation(
        String field,
        String choice,
        Double confidence,
        Map<String, Double> probabilities) {

    TypeSafeChoiceEvaluation {
        probabilities = probabilities == null ? Map.of() : Map.copyOf(probabilities);
    }
}
