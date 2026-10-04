package dev.julioperez.nls.productsearch.infrastructure.http;

import dev.julioperez.nls.productsearch.application.ProductSearchAnswer;
import dev.julioperez.nls.productsearch.application.ProductSearchConversationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products/search")
@Tag(name = "Product search", description = "Structured and natural-language product catalog search.")
public class ProductSearchAnswerController {
    private final ProductSearchConversationService conversationService;

    public ProductSearchAnswerController(ProductSearchConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @PostMapping("/answer")
    @Operation(
            summary = "Search and prepare a customer reply",
            description = "Interprets a message, searches the catalog, and returns both structured results and a customer-facing reply.")
    public ProductSearchAnswerResponse answer(@Valid @RequestBody ProductSearchAnswerRequest request) {
        ProductSearchAnswer answer = conversationService.answer(request.message());
        return new ProductSearchAnswerResponse(answer.outcome(), answer.reply(), answer.results());
    }
}
