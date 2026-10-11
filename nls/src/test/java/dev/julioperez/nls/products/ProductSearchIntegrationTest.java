package dev.julioperez.nls.products;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.julioperez.nls.conversation.application.ConversationMessageCommand;
import dev.julioperez.nls.conversation.application.ConversationMessageService;
import dev.julioperez.nls.conversation.domain.ConversationChannel;
import dev.julioperez.nls.conversation.domain.ConversationIdentity;
import dev.julioperez.nls.conversation.domain.ConversationRepository;
import dev.julioperez.nls.conversation.infrastructure.repository.postgres.ConversationJpaRepository;
import dev.julioperez.nls.products.application.SearchConversationContext;
import dev.julioperez.nls.products.application.SearchDecisionEngine;
import dev.julioperez.nls.products.domain.search.Criteria;
import dev.julioperez.nls.products.domain.search.Filter;
import dev.julioperez.nls.products.domain.search.FilterOperator;
import dev.julioperez.nls.products.infrastructure.repository.postgres.CategoryBaseJpaRepository;
import dev.julioperez.nls.products.infrastructure.repository.postgres.CategoryJpaEntity;
import dev.julioperez.nls.products.infrastructure.repository.postgres.ProductBaseJpaRepository;
import dev.julioperez.nls.products.infrastructure.repository.postgres.ProductJpaEntity;
import dev.julioperez.nls.products.infrastructure.repository.postgres.ProductVariantBaseJpaRepository;
import dev.julioperez.nls.products.infrastructure.repository.postgres.ProductVariantJpaEntity;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = {
        "springdoc.api-docs.enabled=true",
        "springdoc.swagger-ui.enabled=true",
        "nls.database.secret-id=",
        "nls.conversation.identity-hmac-key=integration-test-identity-hmac-key-0123456789abcdef",
        "nls.build.revision=test-build",
        "nls.evaluation.enabled=true",
        "NLS_DB_SCHEMA=public"
})
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Import(ProductSearchIntegrationTest.TestDecisionEngineConfiguration.class)
class ProductSearchIntegrationTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    MockMvc mvc;

    @Autowired
    CategoryBaseJpaRepository categories;

    @Autowired
    ProductBaseJpaRepository products;

    @Autowired
    ProductVariantBaseJpaRepository variants;

    @Autowired
    ConversationRepository conversations;

    @Autowired
    ConversationJpaRepository conversationEntities;

    @Autowired
    SearchContextRecorder searchContextRecorder;

    @Autowired
    ConversationMessageService conversationMessageService;

    @BeforeEach
    void clearCatalog() {
        searchContextRecorder.clear();
        variants.deleteAllInBatch();
        products.deleteAllInBatch();
        categories.deleteAllInBatch();
    }

    @Test
    void reusesTheVariantJoinAndCountsDistinctProducts() throws Exception {
        CategoryJpaEntity category = category("T-Shirts");
        ProductJpaEntity wrongVariantCombination = product("Mixed variants", category);
        variant(wrongVariantCombination, "BLACK", "L", "20.00", 5);
        variant(wrongVariantCombination, "WHITE", "M", "20.00", 5);

        ProductJpaEntity firstMatch = product("Black medium one", category);
        variant(firstMatch, "BLACK", "M", "20.00", 5);
        variant(firstMatch, "BLACK", "M", "25.00", 8);

        ProductJpaEntity secondMatch = product("Black medium two", category);
        variant(secondMatch, "BLACK", "M", "30.00", 3);

        mvc.perform(post("/api/products/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "filters": [
                                    {"field": "color", "operator": "=", "value": "black"},
                                    {"field": "size", "operator": "=", "value": "M"},
                                    {"field": "price", "operator": "<=", "value": 30.00}
                                  ],
                                  "order_by": "productName",
                                  "order": "ASC",
                                  "limit": 1,
                                  "offset": 0
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].name").value("Black medium one"))
                .andExpect(jsonPath("$.limit").value(1));
    }

    @Test
    void exposesSemanticFieldsAndDatabaseBackedFiniteValues() throws Exception {
        CategoryJpaEntity category = category("T-Shirts");
        ProductJpaEntity product = product("Black tee", category);
        variant(product, "BLACK", "M", "49.99", 2);

        mvc.perform(get("/api/products/search/schema"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.context").value("product-search"))
                .andExpect(jsonPath("$.pagination.defaultLimit").value(10))
                .andExpect(jsonPath("$.fields[*].name", hasItems(
                        "category", "color", "price", "productName", "size", "stock")))
                .andExpect(jsonPath("$.fields[?(@.name == 'category')].values[0]").value("T-Shirts"))
                .andExpect(jsonPath("$.fields[?(@.name == 'color')].values[0]").value("BLACK"))
                .andExpect(jsonPath("$.fields[2].sortable").value(false));
    }

    @Test
    void publishesOpenApiDocumentationForSearchEndpoints() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Natural Language Search API"))
                .andExpect(jsonPath("$.paths['/api/products/search/schema'].get.summary").value(
                        "Get the search schema"))
                .andExpect(jsonPath("$.paths['/api/products/search'].post.summary").value(
                        "Search products"))
                .andExpect(jsonPath("$.paths['/api/products/search/interpret'].post.summary").value(
                        "Interpret a message"));
    }

    @Test
    void servesSwaggerUiForLocalEndpointTesting() throws Exception {
        mvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
    }

    @Test
    void rejectsUnknownFieldsAndInvalidOperatorsWithStableErrors() throws Exception {
        mvc.perform(post("/api/products/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"filters":[{"field":"variants.price","operator":"=","value":5}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SEARCH_FIELD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.requestId").exists());

        mvc.perform(post("/api/products/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"filters":[{"field":"price","operator":"CONTAINS","value":"cheap"}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SEARCH_OPERATOR_NOT_ALLOWED"));
    }

    @Test
    void validatesInterpretationOutputBeforeReturningIt() throws Exception {
        CategoryJpaEntity category = category("T-Shirts");
        ProductJpaEntity product = product("Black tee", category);
        variant(product, "BLACK", "M", "49.99", 2);

        mvc.perform(post("/api/products/search/interpret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"black medium\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filters.length()").value(2))
                .andExpect(jsonPath("$.filters[0].field").value("color"))
                .andExpect(jsonPath("$.filters[0].operator").value("="))
                .andExpect(jsonPath("$.filters[1].field").value("size"));
    }

    @Test
    void appliesBoundedPaginationValidationBeforeQueryExecution() throws Exception {
        mvc.perform(post("/api/products/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"filters\":[],\"limit\":51}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SEARCH_REQUEST"));
    }

    @Test
    void retainsSearchCriteriaAcrossConversationMessagesAndReplaysDuplicateProviderMessages() throws Exception {
        CategoryJpaEntity category = category("T-Shirts");
        ProductJpaEntity black = product("Black tee", category);
        variant(black, "BLACK", "M", "25.00", 4);
        ProductJpaEntity white = product("White tee", category);
        variant(white, "WHITE", "M", "20.00", 5);

        String channelAccountId = "business-test";
        String participantId = "customer-" + java.util.UUID.randomUUID();

        mvc.perform(conversationMessage(channelAccountId, participantId, "wamid-test-1", "black medium"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("RESULTS"))
                .andExpect(jsonPath("$.results.total").value(1))
                .andExpect(jsonPath("$.results.items[0].name").value("Black tee"))
                .andExpect(jsonPath("$.criteria.filters.length()").value(2))
                .andExpect(jsonPath("$.context.retainedMessages").value(2));

        mvc.perform(conversationMessage(channelAccountId, participantId, "wamid-test-2", "under 30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("RESULTS"))
                .andExpect(jsonPath("$.results.total").value(1))
                .andExpect(jsonPath("$.results.items[0].name").value("Black tee"))
                .andExpect(jsonPath("$.criteria.filters.length()").value(3))
                .andExpect(jsonPath("$.context.retainedMessages").value(4));

        mvc.perform(conversationMessage(channelAccountId, participantId, "wamid-test-2", "white medium"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.items[0].name").value("Black tee"))
                .andExpect(jsonPath("$.criteria.filters.length()").value(3))
                .andExpect(jsonPath("$.context.retainedMessages").value(4));

        for (int turn = 3; turn <= 11; turn++) {
            mvc.perform(conversationMessage(
                            channelAccountId, participantId, "wamid-test-" + turn, "black medium"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.context.retainedMessages").value(Math.min(turn * 2, 20)));
        }
    }

    @Test
    void restoresTheFirstSavedSearchAfterSeveralContextualSearches() throws Exception {
        CategoryJpaEntity category = category("T-Shirts");
        ProductJpaEntity black = product("Black tee", category);
        variant(black, "BLACK", "M", "25.00", 4);
        ProductJpaEntity white = product("White tee", category);
        variant(white, "WHITE", "M", "20.00", 5);

        String channelAccountId = "business-snapshot-test";
        String participantId = "customer-" + java.util.UUID.randomUUID();

        mvc.perform(conversationMessage(channelAccountId, participantId, "snapshot-1", "black medium"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.items[0].name").value("Black tee"));
        mvc.perform(conversationMessage(channelAccountId, participantId, "snapshot-2", "white medium"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.items[0].name").value("White tee"));
        mvc.perform(conversationMessage(channelAccountId, participantId, "snapshot-3", "volvamos a lo primero"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.items[0].name").value("Black tee"))
                .andExpect(jsonPath("$.criteria.filters[0].value").value("BLACK"));
    }

    @Test
    void resetStartsANewModelContextEpochWhileRetainingRecentMessages() throws Exception {
        CategoryJpaEntity category = category("T-Shirts");
        ProductJpaEntity black = product("Black tee", category);
        variant(black, "BLACK", "M", "25.00", 4);
        ProductJpaEntity white = product("White tee", category);
        variant(white, "WHITE", "M", "20.00", 5);

        String channelAccountId = "context-epoch-test";
        String participantId = "customer-" + java.util.UUID.randomUUID();
        ConversationIdentity identity = new ConversationIdentity(
                ConversationChannel.WHATSAPP, channelAccountId, participantId);

        mvc.perform(conversationMessage(channelAccountId, participantId, "epoch-1", "black medium"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("RESULTS"));
        mvc.perform(conversationMessage(channelAccountId, participantId, "epoch-2", "under 30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("RESULTS"));
        assertThat(searchContextRecorder.last().turns()).hasSize(2);

        var reset = conversationMessageService.resetContext(new ConversationMessageCommand(
                identity, "epoch-reset", "reiniciar"));
        assertThat(reset.outcome().name()).isEqualTo("CONTEXT_RESET");
        int interpretationCallsBeforeOldReference = searchContextRecorder.size();
        mvc.perform(conversationMessage(
                        channelAccountId, participantId, "epoch-old-reference", "volvamos a lo primero"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("NEEDS_CLARIFICATION"));
        assertThat(searchContextRecorder.size()).isEqualTo(interpretationCallsBeforeOldReference);

        mvc.perform(conversationMessage(channelAccountId, participantId, "epoch-reset-again", "empecemos de nuevo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("NEEDS_CLARIFICATION"));

        mvc.perform(conversationMessage(channelAccountId, participantId, "epoch-3", "white medium"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.items[0].name").value("White tee"));
        assertThat(searchContextRecorder.last().turns()).isEmpty();
        assertThat(searchContextRecorder.last().searchSnapshots()).isEmpty();

        mvc.perform(conversationMessage(channelAccountId, participantId, "epoch-4", "black medium"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.items[0].name").value("Black tee"));
        assertThat(searchContextRecorder.last().turns())
                .extracting(SearchConversationContext.Turn::content)
                .hasSize(2)
                .contains("white medium")
                .doesNotContain("under 30", "black medium");
        assertThat(searchContextRecorder.last().searchSnapshots())
                .extracting(SearchConversationContext.SearchSnapshot::sequence)
                .containsExactly(1L);

        var storedMessages = conversations.latestMessages(
                conversations.lockOrCreate(identity).id(), ConversationMessageService.MAX_CONTEXT_MESSAGES);
        assertThat(storedMessages)
                .extracting(message -> message.content())
                .contains("black medium", "under 30");
    }

    @Test
    void runsAndPersistsTheVersionedEndToEndEvaluationCorpus() throws Exception {
        CategoryJpaEntity category = category("REMERAS");
        ProductJpaEntity basic = product("Remera básica de algodón", category);
        variant(basic, "BLANCO", "M", "22990", 8);
        variant(basic, "BLANCO", "L", "23990", 3);
        variant(basic, "NEGRO", "M", "24990", 12);
        variant(basic, "NEGRO", "L", "25990", 4);
        variant(basic, "AZUL", "M", "23990", 5);
        ProductJpaEntity sport = product("Remera deportiva dry fit", category);
        variant(sport, "NEGRO", "M", "27990", 5);
        variant(sport, "AZUL", "M", "26990", 7);
        variant(sport, "BLANCO", "L", "27990", 2);

        String response = mvc.perform(post("/api/evaluations/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"suiteId\":\"catalog-conversation\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andReturn().getResponse().getContentAsString();
        var mapper = tools.jackson.databind.json.JsonMapper.builder().build();
        String runId = mapper.readTree(response).path("id").stringValue();

        org.springframework.test.web.servlet.MvcResult completed = null;
        long deadline = System.nanoTime() + java.time.Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            org.springframework.test.web.servlet.MvcResult current = mvc.perform(get("/api/evaluations/runs/{id}", runId))
                    .andExpect(status().isOk()).andReturn();
            String runStatus = mapper.readTree(current.getResponse().getContentAsString())
                    .path("status").stringValue();
            if ("COMPLETED".equals(runStatus) || "FAILED".equals(runStatus)) {
                completed = current;
                break;
            }
            Thread.sleep(25);
        }
        assertThat(completed).isNotNull();
        mvc.perform(get("/api/evaluations/runs/{id}", runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.suiteId").value("catalog-conversation"))
                .andExpect(jsonPath("$.corpusVersion").value("1.1.1"))
                .andExpect(jsonPath("$.catalogVersion").value("catalog-1.1.0"))
                .andExpect(jsonPath("$.corpusSha256").value(org.hamcrest.Matchers.matchesPattern("[a-f0-9]{64}")))
                .andExpect(jsonPath("$.catalogSha256").value(org.hamcrest.Matchers.matchesPattern("[a-f0-9]{64}")))
                .andExpect(jsonPath("$.applicationRevision").value("test-build"))
                .andExpect(jsonPath("$.caseCount").value(2))
                .andExpect(jsonPath("$.passedCaseCount").value(2))
                .andExpect(jsonPath("$.failedCaseCount").value(0))
                .andExpect(jsonPath("$.report.metrics.providerCalls").value(0))
                .andExpect(jsonPath("$.report.metrics.turnCount").value(6))
                .andExpect(jsonPath("$.report.metrics.passedTurnCount").value(6))
                .andExpect(jsonPath("$.report.metrics.criteriaExactMatchRate").value(1.0))
                .andExpect(jsonPath("$.report.cases[0].turns[0].actual.products[0].variants[0].id").exists())
                .andExpect(jsonPath("$.report.cases[0].turns[3].actual.criteria").isArray());
        mvc.perform(get("/api/evaluations/runs").param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(runId))
                .andExpect(jsonPath("$.items[0].passedCaseCount").value(2));
        assertThat(conversationEntities.countByChannel("API")).isZero();
    }

    @Test
    void exposesVersionedEvaluationSuitesAndTheirCaseCounts() throws Exception {
        mvc.perform(get("/api/evaluations/suites"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.corpusVersion").value("1.1.1"))
                .andExpect(jsonPath("$.catalogVersion").value("catalog-1.1.0"))
                .andExpect(jsonPath("$.suites[?(@.id == 'catalog-conversation-pilot')].caseCount").value(12))
                .andExpect(jsonPath("$.suites[?(@.id == 'catalog-conversation-development')].caseCount").value(45))
                .andExpect(jsonPath("$.suites[?(@.id == 'catalog-conversation-holdout')].caseCount").value(15));
    }

    private CategoryJpaEntity category(String name) {
        return categories.saveAndFlush(new CategoryJpaEntity(name));
    }

    private ProductJpaEntity product(String name, CategoryJpaEntity category) {
        return products.saveAndFlush(new ProductJpaEntity(name, category));
    }

    private void variant(
            ProductJpaEntity product,
            String color,
            String size,
            String price,
            int stock) {
        variants.saveAndFlush(new ProductVariantJpaEntity(
                product, color, size, new BigDecimal(price), stock));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder conversationMessage(
            String channelAccountId,
            String participantId,
            String messageId,
            String message) {
        return post("/api/conversations/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "channel": "WHATSAPP",
                          "channelAccountId": "%s",
                          "participantId": "%s",
                          "messageId": "%s",
                          "message": "%s"
                        }
                        """.formatted(channelAccountId, participantId, messageId, message));
    }

    @TestConfiguration
    static class TestDecisionEngineConfiguration {
        @Bean
        @Primary
        SearchContextRecorder searchContextRecorder() {
            return new SearchContextRecorder();
        }

        @Bean
        @Primary
        SearchDecisionEngine testDecisionEngine(SearchContextRecorder searchContextRecorder) {
            return new SearchDecisionEngine() {
                @Override
                public Criteria interpret(String message, dev.julioperez.nls.products.domain.search.SearchSchema schema) {
                    if ("Hola, ¿tenés remeras?".equals(message)) {
                        return new Criteria(List.of(
                                new Filter("category", FilterOperator.EQUALS, "REMERAS")), null, 10, 0);
                    }
                    if ("Negras, por favor".equals(message)) {
                        return new Criteria(List.of(
                                new Filter("color", FilterOperator.EQUALS, "NEGRO")), null, 10, 0);
                    }
                    if ("Y hasta 26 mil".equals(message)) {
                        return new Criteria(List.of(
                                new Filter("price", FilterOperator.LESS_THAN_OR_EQUAL, new BigDecimal("26000"))),
                                null, 10, 0);
                    }
                    if ("Mejor blancas".equals(message)) {
                        return new Criteria(List.of(
                                new Filter("color", FilterOperator.EQUALS, "BLANCO")), null, 10, 0);
                    }
                    if ("Quiero remeras blancas talle M".equals(message)) {
                        return new Criteria(List.of(
                                new Filter("category", FilterOperator.EQUALS, "REMERAS"),
                                new Filter("color", FilterOperator.EQUALS, "BLANCO"),
                                new Filter("size", FilterOperator.EQUALS, "M")), null, 10, 0);
                    }
                    if ("Mejor negras".equals(message)) {
                        return new Criteria(List.of(
                                new Filter("color", FilterOperator.EQUALS, "NEGRO")), null, 10, 0);
                    }
                    if ("black medium".equals(message)) {
                        return new Criteria(List.of(
                                new Filter("color", FilterOperator.EQUALS, "BLACK"),
                                new Filter("size", FilterOperator.EQUALS, "M")), null, 10, 0);
                    }
                    if ("white medium".equals(message)) {
                        return new Criteria(List.of(
                                new Filter("color", FilterOperator.EQUALS, "WHITE"),
                                new Filter("size", FilterOperator.EQUALS, "M")), null, 10, 0);
                    }
                    if ("under 30".equals(message)) {
                        return new Criteria(List.of(
                                new Filter("price", FilterOperator.LESS_THAN, new BigDecimal("30"))), null, 10, 0);
                    }
                    return null;
                }

                @Override
                public Criteria interpretTurn(
                        String message,
                        dev.julioperez.nls.products.domain.search.SearchSchema schema,
                        Criteria current,
                        dev.julioperez.nls.products.application.SearchConversationContext context) {
                    searchContextRecorder.record(context);
                    if ("volvamos a lo primero".equals(message)
                            && "SEARCH_1".equals(context.resolvedSearchReference())) {
                        return current;
                    }
                    return SearchDecisionEngine.super.interpretTurn(message, schema, current);
                }
            };
        }
    }

    static class SearchContextRecorder {
        private final List<SearchConversationContext> contexts = new CopyOnWriteArrayList<>();

        void record(SearchConversationContext context) {
            contexts.add(context);
        }

        SearchConversationContext last() {
            return contexts.getLast();
        }

        void clear() {
            contexts.clear();
        }

        int size() {
            return contexts.size();
        }
    }
}
