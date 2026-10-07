package dev.julioperez.nls.products;

import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = {
        "springdoc.api-docs.enabled=true",
        "springdoc.swagger-ui.enabled=true",
        "nls.database.secret-id=",
        "nls.conversation.identity-hmac-key=integration-test-identity-hmac-key-0123456789abcdef",
        "NLS_DB_SCHEMA=public"
})
@AutoConfigureMockMvc
@Testcontainers
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

    @BeforeEach
    void clearCatalog() {
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
        SearchDecisionEngine testDecisionEngine() {
            return (message, schema) -> {
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
            };
        }
    }
}
