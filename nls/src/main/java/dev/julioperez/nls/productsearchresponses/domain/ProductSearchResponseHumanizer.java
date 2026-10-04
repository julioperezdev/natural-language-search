package dev.julioperez.nls.productsearchresponses.domain;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public class ProductSearchResponseHumanizer {
    private static final int MAX_PRODUCTS_IN_REPLY = 5;
    private static final int MAX_VARIANTS_IN_REPLY = 3;

    public HumanizedProductSearchResponse humanize(String message, SearchResultFacts results) {
        String searchContext = message == null || message.isBlank() ? "la búsqueda" : "tu pedido";
        if (results.items().isEmpty()) {
            if (results.total() == 0) {
                return new HumanizedProductSearchResponse(
                        SearchResponseOutcome.NO_RESULTS,
                        "No encontré productos que coincidan con " + searchContext
                                + ". Podés cambiar algún filtro y vuelvo a buscar.");
            }
            return new HumanizedProductSearchResponse(
                    SearchResponseOutcome.NO_RESULTS,
                    "No hay productos en esta página. La búsqueda encontró " + results.total()
                            + " en total; podés consultar otra página de resultados.");
        }

        int visibleProducts = Math.min(results.items().size(), MAX_PRODUCTS_IN_REPLY);
        StringBuilder reply = new StringBuilder("Encontré ")
                .append(results.total())
                .append(results.total() == 1
                        ? " producto para " + searchContext + "."
                        : " productos para " + searchContext + ".")
                .append(" Te comparto ")
                .append(visibleProducts == 1 ? "esta opción:\n" : visibleProducts + " opciones:\n");

        results.items().stream()
                .limit(MAX_PRODUCTS_IN_REPLY)
                .forEach(item -> appendProduct(reply, item));

        int omittedProducts = results.items().size() - visibleProducts;
        if (omittedProducts > 0 || results.total() > visibleProducts) {
            reply.append("La búsqueda tiene más resultados; podés recorrerlos con la paginación.");
        }
        return new HumanizedProductSearchResponse(SearchResponseOutcome.RESULTS, reply.toString().stripTrailing());
    }

    private void appendProduct(StringBuilder reply, SearchProductFact product) {
        reply.append("\n• ").append(product.name());
        if (product.category() != null && !product.category().isBlank()) {
            reply.append(" (").append(product.category()).append(")");
        }
        List<SearchVariantFact> variants = product.variants();
        if (variants.isEmpty()) {
            reply.append(". No hay variantes informadas.\n");
            return;
        }

        reply.append(". Variantes que coinciden: ");
        int visibleVariants = Math.min(variants.size(), MAX_VARIANTS_IN_REPLY);
        for (int index = 0; index < visibleVariants; index++) {
            if (index > 0) {
                reply.append("; ");
            }
            appendVariant(reply, variants.get(index));
        }
        if (variants.size() > visibleVariants) {
            reply.append("; y ").append(variants.size() - visibleVariants).append(" más");
        }
        reply.append(".\n");
    }

    private void appendVariant(StringBuilder reply, SearchVariantFact variant) {
        reply.append(variant.color()).append(" / talle ").append(variant.size())
                .append(" — precio ").append(format(variant.price()))
                .append(" — ");
        if (variant.stock() == 0) {
            reply.append("sin unidades disponibles");
        } else {
            reply.append(variant.stock()).append(variant.stock() == 1
                    ? " unidad disponible"
                    : " unidades disponibles");
        }
    }

    private String format(BigDecimal amount) {
        NumberFormat formatter = NumberFormat.getNumberInstance(Locale.forLanguageTag("es-AR"));
        formatter.setGroupingUsed(true);
        formatter.setMaximumFractionDigits(4);
        formatter.setMinimumFractionDigits(0);
        return formatter.format(amount.stripTrailingZeros());
    }
}
