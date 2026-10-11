package dev.julioperez.nls.products.infrastructure.ai.typesafe;

import dev.julioperez.nls.products.domain.search.Criteria;
import dev.julioperez.nls.products.domain.search.Filter;
import dev.julioperez.nls.products.domain.search.FilterOperator;
import dev.julioperez.nls.products.domain.search.SearchFieldSchema;
import dev.julioperez.nls.products.domain.search.SearchFieldType;
import dev.julioperez.nls.products.domain.search.SearchSchema;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Applies unambiguous, short filter updates against the current validated search state. */
final class ConversationalSearchCriteriaRefiner {
    private static final Pattern WORDS = Pattern.compile("[\\p{L}\\p{N}]+");
    private static final Pattern PRICE = Pattern.compile(
            "(?iu)(no\\s+menos\\s+de|no\\s+mas\\s+de|no\\s+más\\s+de|como\\s+maximo|como\\s+máximo|"
                    + "como\\s+minimo|como\\s+mínimo|por\\s+debajo\\s+de|por\\s+encima\\s+de|al\\s+menos|"
                    + "menos\\s+de|inferior\\s+a|menor\\s+que|hasta|mas\\s+de|más\\s+de|superior\\s+a|"
                    + "mayor\\s+que|desde|con\\s+(?:un\\s+)?tope(?:\\s+de)?|tope(?:\\s+de)?)"
                    + "\\s*\\$?\\s*(\\d[\\d.,]*)\\s*(mil|k|lucas?|pesos?)?");
    private static final Pattern BUDGET = Pattern.compile(
            "(?iu)\\b(?:(?:solo|solamente)\\s+)?(?:tengo|cuento\\s+con|dispongo\\s+de|"
                    + "mi\\s+presupuesto\\s+(?:es(?:\\s+de)?|de)|presupuesto(?:\\s+de)?)"
                    + "\\s*\\$?\\s*(\\d[\\d.,]*)\\s*(mil|k|lucas?|pesos?)?\\b");
    private static final Pattern STOCK_AVAILABLE = Pattern.compile("\\b(con stock|disponible|disponibles)\\b");
    private static final Set<String> REFINEMENT_FILLER_WORDS = Set.of(
            "al", "algo", "con", "del", "el", "en", "es", "esta", "este", "favor", "hay", "hola",
            "me", "mejor", "mi", "mil", "necesito", "para", "por", "producto", "productos", "quiero",
            "quisiera", "remera", "remeras", "tambien", "tenes", "tengo", "tenia", "tenias", "tenian",
            "tienes", "una", "un", "unas", "unos", "y", "presupuesto", "cuento", "dispongo", "k", "luca",
            "lucas", "pesos", "que", "sean", "talle", "talla", "porfa");

    private ConversationalSearchCriteriaRefiner() {
    }

    static List<Filter> deterministicFilters(String message, SearchSchema schema, Criteria current) {
        List<Filter> filters = new ArrayList<>();
        SearchFieldSchema price = schema.fields().stream()
                .filter(field -> field.name().equals("price") && field.filterable()
                        && field.type() == SearchFieldType.NUMBER)
                .findFirst().orElse(null);
        if (price != null) {
            Matcher explicitPrice = PRICE.matcher(message);
            if (explicitPrice.find()) {
                FilterOperator operator = priceOperator(explicitPrice.group(1));
                if (price.operators().contains(operator.value())) {
                    filters.add(new Filter(price.name(), operator, amount(explicitPrice.group(2), explicitPrice.group(3))));
                }
            } else if (current != null && !current.filters().isEmpty()) {
                Matcher budget = BUDGET.matcher(message);
                if (budget.find() && price.operators().contains(FilterOperator.LESS_THAN_OR_EQUAL.value())) {
                    filters.add(new Filter(price.name(), FilterOperator.LESS_THAN_OR_EQUAL,
                            amount(budget.group(1), budget.group(2))));
                }
            }
        }

        SearchFieldSchema stock = schema.fields().stream()
                .filter(field -> field.name().equals("stock") && field.filterable()
                        && field.type() == SearchFieldType.INTEGER)
                .findFirst().orElse(null);
        String normalized = normalize(message);
        if (stock != null && stock.operators().contains(FilterOperator.GREATER_THAN_OR_EQUAL.value())
                && STOCK_AVAILABLE.matcher(normalized).find()) {
            filters.add(new Filter(stock.name(), FilterOperator.GREATER_THAN_OR_EQUAL, 1));
        }
        return List.copyOf(filters);
    }

    static Optional<Refinement> refine(
            String message,
            SearchSchema schema,
            Criteria current,
            List<Filter> deterministicFilters) {
        if (current == null || current.filters().isEmpty() || message == null || message.isBlank()) {
            return Optional.empty();
        }

        String normalized = normalize(message);
        boolean[] consumed = new boolean[normalized.length()];
        boolean hasDeterministicPrice = deterministicFilters.stream()
                .anyMatch(filter -> filter.field().equals("price"));
        if (hasDeterministicPrice) {
            consumeMatches(consumed, PRICE, normalized);
            consumeMatches(consumed, BUDGET, normalized);
        }
        Map<String, Set<String>> enumChoices = new LinkedHashMap<>();
        for (SearchFieldSchema field : schema.fields()) {
            if (field.type() != SearchFieldType.ENUM || !field.filterable()
                    || !field.operators().contains(FilterOperator.EQUALS.value())) {
                continue;
            }
            Set<String> values = new LinkedHashSet<>();
            for (String value : field.values()) {
                for (String alias : aliases(field, value)) {
                    Matcher matcher = phrase(alias).matcher(normalized);
                    while (matcher.find()) {
                        if (overlapsConsumed(consumed, matcher.start(), matcher.end())) {
                            continue;
                        }
                        values.add(value);
                        consume(consumed, matcher.start(), matcher.end());
                    }
                }
            }
            if (!values.isEmpty()) {
                if (values.size() > 1) {
                    return Optional.empty();
                }
                enumChoices.put(field.name(), values);
            }
        }

        List<Filter> updates = new ArrayList<>();
        enumChoices.forEach((field, values) -> updates.add(
                new Filter(field, FilterOperator.EQUALS, values.iterator().next())));
        updates.addAll(deterministicFilters);
        if (updates.isEmpty()) {
            return Optional.empty();
        }

        if (deterministicFilters.stream().anyMatch(filter -> filter.field().equals("price"))) {
            consumeMatches(consumed, PRICE, normalized);
            consumeMatches(consumed, BUDGET, normalized);
        }
        if (deterministicFilters.stream().anyMatch(filter -> filter.field().equals("stock"))) {
            consumeMatches(consumed, STOCK_AVAILABLE, normalized);
        }
        if (containsUnrecognizedWords(normalized, consumed)) {
            return Optional.empty();
        }

        List<Filter> merged = new ArrayList<>(current.filters());
        for (Filter update : updates) {
            merged.removeIf(existing -> existing.field().equals(update.field()));
            merged.add(update);
        }
        return Optional.of(new Refinement(
                new Criteria(merged, current.order(), current.limit(), 0),
                updates.stream().map(Filter::field).distinct().toList()));
    }

    private static boolean containsUnrecognizedWords(String normalized, boolean[] consumed) {
        Matcher words = WORDS.matcher(normalized);
        while (words.find()) {
            boolean matched = false;
            for (int index = words.start(); index < words.end(); index++) {
                if (consumed[index]) {
                    matched = true;
                    break;
                }
            }
            if (matched) {
                continue;
            }
            String word = words.group();
            if (!REFINEMENT_FILLER_WORDS.contains(word) && !word.matches("\\d+")) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> aliases(SearchFieldSchema field, String value) {
        String normalized = normalize(value);
        Set<String> aliases = new LinkedHashSet<>();
        aliases.add(normalized);
        if (field.name().equals("color")) {
            aliases.addAll(colorAliases(normalized));
        }
        if (field.name().equals("category")) {
            if (normalized.endsWith("s") && normalized.length() > 3) {
                aliases.add(normalized.substring(0, normalized.length() - 1));
            } else if (normalized.length() > 3) {
                aliases.add(normalized + "s");
            }
        }
        return aliases;
    }

    private static Set<String> colorAliases(String value) {
        return switch (value) {
            case "black", "negro", "negra", "negros", "negras" ->
                    Set.of("black", "negro", "negra", "negros", "negras");
            case "white", "blanco", "blanca", "blancos", "blancas" ->
                    Set.of("white", "blanco", "blanca", "blancos", "blancas");
            case "blue", "azul", "azules" -> Set.of("blue", "azul", "azules");
            case "red", "rojo", "roja", "rojos", "rojas" -> Set.of("red", "rojo", "roja", "rojos", "rojas");
            case "green", "verde", "verdes" -> Set.of("green", "verde", "verdes");
            case "gray", "grey", "gris", "grises" -> Set.of("gray", "grey", "gris", "grises");
            case "pink", "rosa", "rosado", "rosada", "rosados", "rosadas" ->
                    Set.of("pink", "rosa", "rosado", "rosada", "rosados", "rosadas");
            case "yellow", "amarillo", "amarilla", "amarillos", "amarillas" ->
                    Set.of("yellow", "amarillo", "amarilla", "amarillos", "amarillas");
            default -> Set.of(value);
        };
    }

    private static Pattern phrase(String value) {
        return Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(value)
                + "(?![\\p{L}\\p{N}])");
    }

    private static void consumeMatches(boolean[] consumed, Pattern pattern, String value) {
        Matcher matcher = pattern.matcher(value);
        while (matcher.find()) {
            consume(consumed, matcher.start(), matcher.end());
        }
    }

    private static void consume(boolean[] consumed, int start, int end) {
        Arrays.fill(consumed, start, end, true);
    }

    private static boolean overlapsConsumed(boolean[] consumed, int start, int end) {
        for (int index = start; index < end; index++) {
            if (consumed[index]) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
    }

    private static FilterOperator priceOperator(String comparator) {
        String normalized = normalize(comparator).replaceAll("\\s+", " ").trim();
        if (normalized.contains("no menos de") || normalized.contains("desde") || normalized.contains("al menos")
                || normalized.contains("minimo")) {
            return FilterOperator.GREATER_THAN_OR_EQUAL;
        }
        if (normalized.equals("hasta") || normalized.contains("maximo") || normalized.contains("no mas de")
                || normalized.contains("tope")) {
            return FilterOperator.LESS_THAN_OR_EQUAL;
        }
        if (normalized.contains("menos de") || normalized.contains("debajo") || normalized.contains("inferior")
                || normalized.contains("menor que")) {
            return FilterOperator.LESS_THAN;
        }
        return FilterOperator.GREATER_THAN;
    }

    private static BigDecimal amount(String number, String unit) {
        String normalized = number;
        if (normalized.contains(",")) {
            normalized = normalized.replace(".", "").replace(',', '.');
        } else if (normalized.matches("\\d{1,3}(\\.\\d{3})+")) {
            normalized = normalized.replace(".", "");
        }
        BigDecimal value = new BigDecimal(normalized);
        if (unit != null && Set.of("mil", "k", "luca", "lucas").contains(normalize(unit))) {
            value = value.multiply(BigDecimal.valueOf(1_000));
        }
        return value;
    }

    record Refinement(Criteria criteria, List<String> updatedFields) {
        Refinement {
            updatedFields = List.copyOf(updatedFields);
        }
    }
}
