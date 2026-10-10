package dev.julioperez.nls.products.infrastructure.ai.typesafe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

import dev.julioperez.nls.products.application.SearchInterpretationFailedException;
import dev.julioperez.nls.products.application.SearchInterpretationUnavailableException;
import dev.julioperez.nls.products.application.SearchConversationContext;
import dev.julioperez.nls.products.domain.search.Criteria;
import dev.julioperez.nls.products.domain.search.Filter;
import dev.julioperez.nls.products.domain.search.FilterOperator;
import dev.julioperez.nls.products.domain.search.SearchFieldSchema;
import dev.julioperez.nls.products.domain.search.SearchFieldType;
import dev.julioperez.nls.products.domain.search.SearchPaginationSchema;
import dev.julioperez.nls.products.domain.search.SearchSchema;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class TypeSafeSearchDecisionEngineTest {
    private static final String MESSAGE = "Quiero remeras negras talle M hasta 50 mil";

    @Test
    void sendsSchemaBoundQuestionsAndExtractsSearchCriteria() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TypeSafeSearchDecisionEngine engine = new TypeSafeSearchDecisionEngine(
                builder.build(), properties(), () -> "synthetic-typesafe-key");

        server.expect(requestTo("https://api.typesafe.ai/v1/systemone"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer synthetic-typesafe-key"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.model").value("jev-1.13.0"))
                .andExpect(jsonPath("$.state").value(MESSAGE))
                .andExpect(jsonPath("$.questions.category.criteria.REMERAS").exists())
                .andExpect(jsonPath("$.questions.color.criteria.BLACK").exists())
                .andExpect(jsonPath("$.questions.size.criteria.M").exists())
                .andRespond(withSuccess(response(), MediaType.APPLICATION_JSON));

        var criteria = engine.interpret(MESSAGE, schema());

        assertThat(criteria.filters())
                .extracting("field")
                .containsExactly("price", "category", "color", "size");
        assertThat(criteria.filters().getFirst().operator()).isEqualTo(FilterOperator.LESS_THAN_OR_EQUAL);
        assertThat(criteria.filters().getFirst().value()).isEqualTo(new BigDecimal("50000"));
        assertThat(criteria.limit()).isEqualTo(10);
        server.verify();
    }

    @Test
    void evaluationContextIsStructuredAndKeepsTheLatestMessageAndValidatedCriteriaSeparate() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TypeSafeSearchDecisionEngine engine = new TypeSafeSearchDecisionEngine(
                builder.build(), properties(), () -> "synthetic-typesafe-key");
        Criteria current = new Criteria(List.of(
                new Filter("category", FilterOperator.EQUALS, "REMERAS"),
                new Filter("size", FilterOperator.EQUALS, "M")), null, 10, 0);
        SearchConversationContext context = new SearchConversationContext(List.of(
                new SearchConversationContext.Turn(SearchConversationContext.Role.USER, "¿Qué remeras talle M hay?"),
                new SearchConversationContext.Turn(SearchConversationContext.Role.ASSISTANT,
                        "Primera opción blanca; segunda opción negra.")));
        String contextualResponse = """
                {"model":"jev-1.13.0","usage":{"input_tokens":123,"output_tokens":9},"answers":{
                  "searchIntent":%s,
                  "category":%s,
                  "color":%s,
                  "size":%s,
                  "productName":%s
                }}
                """.formatted(
                answer("SEARCH_PRODUCTS", List.of("SEARCH_PRODUCTS", "BROWSE_CATALOG", "NOT_PRODUCT_SEARCH")),
                answer("__KEEP__", List.of("REMERAS", "BUZOS", "__KEEP__", "__CLEAR__")),
                answer("BLACK", List.of("BLACK", "WHITE", "__NONE__")),
                answer("__KEEP__", List.of("M", "L", "__KEEP__", "__CLEAR__")),
                answer("__NONE__", List.of("PRODUCT_1", "PRODUCT_2", "__NONE__")));

        server.expect(requestTo("https://api.typesafe.ai/v1/systemone"))
                .andExpect(jsonPath("$.state.latestMessage").value("Me quedo con la segunda."))
                .andExpect(jsonPath("$.state.currentValidatedCriteria.filters[0].field").value("category"))
                .andExpect(jsonPath("$.state.conversationHistory[0].role").value("user"))
                .andExpect(jsonPath("$.state.conversationHistory[1].role").value("assistant"))
                .andExpect(jsonPath("$.questions.searchIntent.instructions")
                        .value(org.hamcrest.Matchers.containsString("classify a correction, selection")))
                .andRespond(withSuccess(contextualResponse, MediaType.APPLICATION_JSON));

        TypeSafeInterpretationEvaluation result = engine.interpretForEvaluation(
                "Me quedo con la segunda.", schema(), current, context);

        assertThat(result.inputTokens()).isEqualTo(123);
        assertThat(result.outputTokens()).isEqualTo(9);
        assertThat(result.method()).isEqualTo("typesafe");
        assertThat(result.providerModel()).isEqualTo("jev-1.13.0");
        assertThat(result.providerDecisions())
                .anySatisfy(decision -> {
                    assertThat(decision.field()).isEqualTo("color");
                    assertThat(decision.choice()).isEqualTo("BLACK");
                    assertThat(decision.confidence()).isEqualTo(0.9);
                    assertThat(decision.probabilities()).containsEntry("BLACK", 0.9);
                    assertThat(decision.probabilities()).containsKeys("WHITE", "__NONE__");
                });
        assertThat(value(result.criteria(), "category")).isEqualTo("REMERAS");
        server.verify();
    }

    @Test
    void restoresAReferencedValidatedSearchBeforeApplyingTheLatestCorrection() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TypeSafeSearchDecisionEngine engine = new TypeSafeSearchDecisionEngine(
                builder.build(), properties(), () -> "synthetic-typesafe-key");
        Criteria firstSearch = criteria("BUZOS", "BLACK", "L");
        Criteria current = criteria("REMERAS", "WHITE", "L");
        SearchConversationContext context = new SearchConversationContext(
                List.of(new SearchConversationContext.Turn(
                        SearchConversationContext.Role.USER, "Busco buzos negros talle L")),
                List.of(new SearchConversationContext.SearchSnapshot(1, firstSearch, 2)));
        String response = """
                {"answers":{
                  "searchReference":%s,
                  "searchIntent":%s,
                  "category":%s,
                  "color":%s,
                  "size":%s
                }}
                """.formatted(
                answer("SEARCH_1", List.of("CURRENT_SEARCH", "SEARCH_1")),
                answer("SEARCH_PRODUCTS", List.of("SEARCH_PRODUCTS", "BROWSE_CATALOG", "NOT_PRODUCT_SEARCH")),
                answer("__KEEP__", List.of("REMERAS", "BUZOS", "__KEEP__", "__CLEAR__")),
                answer("__KEEP__", List.of("BLACK", "WHITE", "__KEEP__", "__CLEAR__")),
                answer("M", List.of("M", "L", "__KEEP__", "__CLEAR__")));

        server.expect(requestTo("https://api.typesafe.ai/v1/systemone"))
                .andExpect(jsonPath("$.state.validatedSearchSnapshots[0].reference").value("SEARCH_1"))
                .andExpect(jsonPath("$.questions.searchReference.criteria.SEARCH_1")
                        .value(org.hamcrest.Matchers.containsString("BUZOS")))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        Criteria restored = engine.interpretTurn(
                "Volvamos a lo primero, pero en M", schemaWithoutProductName(), current, context);

        assertThat(value(restored, "category")).isEqualTo("BUZOS");
        assertThat(value(restored, "color")).isEqualTo("BLACK");
        assertThat(value(restored, "size")).isEqualTo("M");
        server.verify();
    }

    @Test
    void mapsOnlyAProviderSelectedPhraseToTheSemanticProductNameField() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TypeSafeSearchDecisionEngine engine = new TypeSafeSearchDecisionEngine(
                builder.build(), properties(), () -> "synthetic-typesafe-key");
        String message = "Quiero Samsung Galaxy S24";
        server.expect(requestTo("https://api.typesafe.ai/v1/systemone"))
                .andExpect(jsonPath("$.questions.productName.criteria.PRODUCT_1").value(
                        "Exact phrase from the user: Samsung Galaxy S24"))
                .andRespond(withSuccess(productNameResponse(), MediaType.APPLICATION_JSON));

        var criteria = engine.interpret(message, schema());

        assertThat(criteria.filters()).hasSize(1);
        assertThat(criteria.filters().getFirst().field()).isEqualTo("productName");
        assertThat(criteria.filters().getFirst().operator()).isEqualTo(FilterOperator.CONTAINS);
        assertThat(criteria.filters().getFirst().value()).isEqualTo("Samsung Galaxy S24");
        server.verify();
    }

    @Test
    void appliesAConversationalTurnToExistingCriteriaAndCanClearOneField() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TypeSafeSearchDecisionEngine engine = new TypeSafeSearchDecisionEngine(
                builder.build(), properties(), () -> "synthetic-typesafe-key");
        List<String> activeChoices = List.of("REMERAS", "BUZOS", "__KEEP__", "__CLEAR__");
        List<String> activeSizes = List.of("M", "L", "__KEEP__", "__CLEAR__");
        server.expect(requestTo("https://api.typesafe.ai/v1/systemone"))
                .andExpect(jsonPath("$.questions.category.criteria.__KEEP__").exists())
                .andExpect(jsonPath("$.questions.color.criteria.__CLEAR__").exists())
                .andExpect(jsonPath("$.questions.size.criteria.__KEEP__").exists())
                .andRespond(withSuccess("""
                        {"answers":{
                          "searchIntent":%s,
                          "category":%s,
                          "color":%s,
                          "size":%s
                        }}
                        """.formatted(
                        answer("SEARCH_PRODUCTS",
                                List.of("SEARCH_PRODUCTS", "BROWSE_CATALOG", "NOT_PRODUCT_SEARCH")),
                        answer("__KEEP__", activeChoices),
                        answer("__KEEP__", List.of("BLACK", "WHITE", "__KEEP__", "__CLEAR__")),
                        answer("__CLEAR__", activeSizes)), MediaType.APPLICATION_JSON));

        Criteria previous = new Criteria(List.of(
                new Filter("category", FilterOperator.EQUALS, "REMERAS"),
                new Filter("color", FilterOperator.EQUALS, "BLACK"),
                new Filter("size", FilterOperator.EQUALS, "M")), null, 10, 0);

        Criteria updated = engine.interpretTurn("solo las de menos de 40 mil", schema(), previous);

        assertThat(updated.filters())
                .extracting("field")
                .containsExactly("category", "color", "price");
        assertThat(updated.filters().getFirst().value()).isEqualTo("REMERAS");
        assertThat(updated.filters().get(1).value()).isEqualTo("BLACK");
        assertThat(updated.filters().get(2).operator()).isEqualTo(FilterOperator.LESS_THAN);
        assertThat(updated.filters().get(2).value()).isEqualTo(new BigDecimal("40000"));
        server.verify();
    }

    @Test
    void appliesColloquialPriceRefinementsWithoutDroppingActiveFilters() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TypeSafeSearchDecisionEngine engine = new TypeSafeSearchDecisionEngine(
                builder.build(), properties(), () -> {
                    throw new AssertionError("Deterministic refinements must not call TypeSafe.");
                });
        Criteria current = new Criteria(List.of(
                new Filter("category", FilterOperator.EQUALS, "REMERAS"),
                new Filter("color", FilterOperator.EQUALS, "BLACK")), null, 10, 0);

        Criteria explicitBudget = engine.interpretTurn("Y hasta 25mil", schema(), current);
        Criteria conversationalBudget = engine.interpretTurn("Solo tengo 25mil", schema(), explicitBudget);

        assertThat(value(explicitBudget, "category")).isEqualTo("REMERAS");
        assertThat(value(explicitBudget, "color")).isEqualTo("BLACK");
        assertThat(filter(explicitBudget, "price").operator()).isEqualTo(FilterOperator.LESS_THAN_OR_EQUAL);
        assertThat(filter(explicitBudget, "price").value()).isEqualTo(new BigDecimal("25000"));
        assertThat(value(conversationalBudget, "category")).isEqualTo("REMERAS");
        assertThat(value(conversationalBudget, "color")).isEqualTo("BLACK");
        assertThat(filter(conversationalBudget, "price").value()).isEqualTo(new BigDecimal("25000"));
        server.verify();
    }

    @Test
    void replacesColloquialColorAndKeepsPriceAndCategoryForTheNextMessage() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TypeSafeSearchDecisionEngine engine = new TypeSafeSearchDecisionEngine(
                builder.build(), properties(), () -> {
                    throw new AssertionError("Deterministic refinements must not call TypeSafe.");
                });
        Criteria current = new Criteria(List.of(
                new Filter("category", FilterOperator.EQUALS, "REMERAS"),
                new Filter("color", FilterOperator.EQUALS, "BLACK"),
                new Filter("price", FilterOperator.LESS_THAN_OR_EQUAL, new BigDecimal("25000"))),
                null, 10, 0);

        Criteria white = engine.interpretTurn("Mejor blancas", schema(), current);
        Criteria repeatedCategory = engine.interpretTurn("Tenés remeras?", schema(), white);

        assertThat(value(white, "category")).isEqualTo("REMERAS");
        assertThat(value(white, "color")).isEqualTo("WHITE");
        assertThat(filter(white, "price").value()).isEqualTo(new BigDecimal("25000"));
        assertThat(value(repeatedCategory, "category")).isEqualTo("REMERAS");
        assertThat(value(repeatedCategory, "color")).isEqualTo("WHITE");
        assertThat(filter(repeatedCategory, "price").value()).isEqualTo(new BigDecimal("25000"));
        server.verify();
    }

    @Test
    void sendsAmbiguousRelativePriceRefinementsToTypeSafeInsteadOfApplyingThemDirectly() {
        Criteria current = new Criteria(List.of(
                new Filter("category", FilterOperator.EQUALS, "REMERAS"),
                new Filter("color", FilterOperator.EQUALS, "BLACK"),
                new Filter("size", FilterOperator.EQUALS, "M")), null, 10, 0);
        String message = "solo las de menos de 40 mil";
        List<Filter> deterministic = ConversationalSearchCriteriaRefiner.deterministicFilters(
                message, schema(), current);

        assertThat(deterministic).hasSize(1);
        assertThat(ConversationalSearchCriteriaRefiner.refine(message, schema(), current, deterministic))
                .isEmpty();
    }

    @Test
    void rejectsInvalidProviderChoicesAndMapsProviderHttpErrorsToUnavailable() {
        RestClient.Builder invalidBuilder = RestClient.builder();
        MockRestServiceServer invalidServer = MockRestServiceServer.bindTo(invalidBuilder).build();
        TypeSafeSearchDecisionEngine invalidEngine = new TypeSafeSearchDecisionEngine(
                invalidBuilder.build(), properties(), () -> "synthetic-typesafe-key");
        invalidServer.expect(requestTo("https://api.typesafe.ai/v1/systemone"))
                .andRespond(withSuccess(invalidProviderResponse(), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> invalidEngine.interpret(MESSAGE, schema()))
                .isInstanceOf(SearchInterpretationFailedException.class)
                .hasMessage("Search intent could not be interpreted safely.");
        invalidServer.verify();

        RestClient.Builder unauthorizedBuilder = RestClient.builder();
        MockRestServiceServer unauthorizedServer = MockRestServiceServer.bindTo(unauthorizedBuilder).build();
        TypeSafeSearchDecisionEngine unauthorizedEngine = new TypeSafeSearchDecisionEngine(
                unauthorizedBuilder.build(), properties(), () -> "synthetic-typesafe-key");
        unauthorizedServer.expect(requestTo("https://api.typesafe.ai/v1/systemone"))
                .andRespond(withUnauthorizedRequest().body("private provider response"));

        assertThatThrownBy(() -> unauthorizedEngine.interpret(MESSAGE, schema()))
                .isInstanceOf(SearchInterpretationUnavailableException.class)
                .hasMessage("Natural-language interpretation is temporarily unavailable.")
                .hasNoCause();
        unauthorizedServer.verify();
    }

    @Test
    void doesNotCallTypeSafeWhenTheSecretCannotBeLoaded() {
        TypeSafeSearchDecisionEngine engine = new TypeSafeSearchDecisionEngine(
                RestClient.builder().build(), properties(), () -> {
                    throw new SearchInterpretationUnavailableException();
                });

        assertThatThrownBy(() -> engine.interpret(MESSAGE, schema()))
                .isInstanceOf(SearchInterpretationUnavailableException.class);
    }

    private static TypeSafeSearchProperties properties() {
        return new TypeSafeSearchProperties(
                "https://api.typesafe.ai", "jev-1.13.0", "wcs/prod/typesafe", Duration.ofSeconds(3), 0.65);
    }

    private static SearchSchema schema() {
        return new SearchSchema("product-search", List.of(
                field("category", SearchFieldType.ENUM, List.of("=", "IN"), List.of("REMERAS", "BUZOS"), false),
                field("color", SearchFieldType.ENUM, List.of("=", "IN"), List.of("BLACK", "WHITE"), false),
                field("price", SearchFieldType.NUMBER, List.of("=", "<", "<=", ">", ">="), List.of(), false),
                field("productName", SearchFieldType.STRING, List.of("=", "CONTAINS"), List.of(), true),
                field("size", SearchFieldType.ENUM, List.of("=", "IN"), List.of("M", "L"), false),
                field("stock", SearchFieldType.INTEGER, List.of("=", ">", ">=", "<", "<="), List.of(), false)),
                new SearchPaginationSchema(10, 50));
    }

    private static SearchSchema schemaWithoutProductName() {
        SearchSchema full = schema();
        return new SearchSchema("product-search", full.fields().stream()
                .filter(field -> !field.name().equals("productName"))
                .toList(), full.pagination());
    }

    private static Criteria criteria(String category, String color, String size) {
        return new Criteria(List.of(
                new Filter("category", FilterOperator.EQUALS, category),
                new Filter("color", FilterOperator.EQUALS, color),
                new Filter("size", FilterOperator.EQUALS, size)), null, 10, 0);
    }

    private static SearchFieldSchema field(
            String name,
            SearchFieldType type,
            List<String> operators,
            List<String> values,
            boolean sortable) {
        return new SearchFieldSchema(name, name, type, operators, values, true, sortable);
    }

    private static Filter filter(Criteria criteria, String field) {
        return criteria.filters().stream()
                .filter(candidate -> candidate.field().equals(field))
                .findFirst()
                .orElseThrow();
    }

    private static Object value(Criteria criteria, String field) {
        return filter(criteria, field).value();
    }

    private static String response() {
        return """
                {"model":"jev-1.13.0","answers":{
                  "searchIntent":%s,
                  "category":%s,
                  "color":%s,
                  "size":%s
                }}
                """.formatted(
                answer("SEARCH_PRODUCTS", List.of("SEARCH_PRODUCTS", "BROWSE_CATALOG", "NOT_PRODUCT_SEARCH")),
                answer("REMERAS", List.of("REMERAS", "BUZOS", "__NONE__")),
                answer("BLACK", List.of("BLACK", "WHITE", "__NONE__")),
                answer("M", List.of("M", "L", "__NONE__")));
    }

    private static String productNameResponse() {
        List<String> productNameOptions = List.of(
                "PRODUCT_1", "PRODUCT_2", "PRODUCT_3", "PRODUCT_4", "PRODUCT_5", "PRODUCT_6", "__NONE__");
        return """
                {"answers":{
                  "searchIntent":%s,
                  "category":%s,
                  "color":%s,
                  "size":%s,
                  "productName":%s
                }}
                """.formatted(
                answer("SEARCH_PRODUCTS", List.of("SEARCH_PRODUCTS", "BROWSE_CATALOG", "NOT_PRODUCT_SEARCH")),
                answer("__NONE__", List.of("REMERAS", "BUZOS", "__NONE__")),
                answer("__NONE__", List.of("BLACK", "WHITE", "__NONE__")),
                answer("__NONE__", List.of("M", "L", "__NONE__")),
                answer("PRODUCT_1", productNameOptions));
    }

    private static String invalidProviderResponse() {
        String invalidCategory = answer("NOT_A_CATEGORY", List.of("REMERAS", "BUZOS", "__NONE__"));
        return """
                {"answers":{"searchIntent":%s,"category":%s}}
                """.formatted(
                answer("SEARCH_PRODUCTS", List.of("SEARCH_PRODUCTS", "BROWSE_CATALOG", "NOT_PRODUCT_SEARCH")),
                invalidCategory);
    }

    private static String answer(String choice, List<String> options) {
        String selectedOption = options.contains(choice) ? choice : options.getFirst();
        Map<String, Double> probabilities = options.stream().collect(Collectors.toMap(
                option -> option,
                option -> option.equals(selectedOption) ? 0.9 : 0.1 / (options.size() - 1)));
        String probabilityJson = probabilities.entrySet().stream()
                .map(entry -> "\"" + entry.getKey() + "\":" + entry.getValue())
                .collect(Collectors.joining(","));
        return "{\"type\":\"choice\",\"choice\":\"" + choice
                + "\",\"confidence\":0.9,\"probabilities\":{" + probabilityJson + "}}";
    }

}
