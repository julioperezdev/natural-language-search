package dev.julioperez.nls.products.application;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/** Resolves explicit references to ordered searches; it does not interpret catalog filter values. */
public final class SearchSnapshotReferenceResolver {
    private static final String RETURN_VERB =
            "(?:volvamos|volver|volve|vuelve|volvemos|retomemos|retomar|retoma|recuperemos|recuperar|recupera|"
                    + "regresemos|regresar|regresa)";
    private static final Pattern FIRST_SEARCH = Pattern.compile(
            "\\b" + RETURN_VERB + "\\b.{0,60}\\b(?:lo\\s+primero|lo\\s+primer|busqueda\\s+inicial)\\b"
                    + "|\\b(?:primera|primer)\\s+busqueda\\b|\\bbusqueda\\s+(?:primera|inicial)\\b");
    private static final Pattern PREVIOUS_SEARCH = Pattern.compile(
            "\\b" + RETURN_VERB + "\\b.{0,60}\\ba\\s+(?:la|lo)\\s+(?:anterior|previa|previo)\\b"
            + "|\\b(?:busqueda|consulta)\\s+(?:anterior|previa|previo)\\b"
                    + "|\\b" + RETURN_VERB + "\\b.{0,60}\\bde\\s+antes\\b");
    private static final Map<String, Integer> ORDINALS = Map.ofEntries(
            Map.entry("segundo", 2), Map.entry("segunda", 2),
            Map.entry("tercero", 3), Map.entry("tercera", 3),
            Map.entry("cuarto", 4), Map.entry("cuarta", 4),
            Map.entry("quinto", 5), Map.entry("quinta", 5),
            Map.entry("sexto", 6), Map.entry("sexta", 6),
            Map.entry("septimo", 7), Map.entry("septima", 7),
            Map.entry("octavo", 8), Map.entry("octava", 8),
            Map.entry("noveno", 9), Map.entry("novena", 9),
            Map.entry("decimo", 10), Map.entry("decima", 10));
    private static final Pattern ORDINAL_SEARCH = Pattern.compile(
            "\\b" + RETURN_VERB + "\\b"
                    + ".{0,60}\\b(segundo|segunda|tercero|tercera|cuarto|cuarta|quinto|quinta|"
                    + "sexto|sexta|septimo|septima|octavo|octava|noveno|novena|decimo|decima)"
                    + "\\s+(?:busqueda|consulta)\\b");

    private SearchSnapshotReferenceResolver() {
    }

    public static Optional<SearchConversationContext.SearchSnapshot> resolve(
            String message,
            List<SearchConversationContext.SearchSnapshot> snapshots) {
        if (message == null || message.isBlank() || snapshots == null || snapshots.isEmpty()) {
            return Optional.empty();
        }
        String normalized = normalize(message);
        if (FIRST_SEARCH.matcher(normalized).find()) {
            return snapshots.stream().filter(snapshot -> snapshot.sequence() == 1).findFirst();
        }
        if (PREVIOUS_SEARCH.matcher(normalized).find()) {
            return snapshots.size() < 2
                    ? Optional.empty()
                    : Optional.of(snapshots.get(snapshots.size() - 2));
        }
        var ordinalMatcher = ORDINAL_SEARCH.matcher(normalized);
        if (ordinalMatcher.find()) {
            Integer sequence = ORDINALS.get(ordinalMatcher.group(1));
            if (sequence != null) {
                return snapshots.stream().filter(snapshot -> snapshot.sequence() == sequence).findFirst();
            }
        }
        return Optional.empty();
    }

    public static boolean isExplicitReference(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String normalized = normalize(message);
        return FIRST_SEARCH.matcher(normalized).find()
                || PREVIOUS_SEARCH.matcher(normalized).find()
                || ORDINAL_SEARCH.matcher(normalized).find();
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT);
    }
}
