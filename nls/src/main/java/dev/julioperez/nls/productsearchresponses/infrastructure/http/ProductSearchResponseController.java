package dev.julioperez.nls.productsearchresponses.infrastructure.http;

import dev.julioperez.nls.productsearchresponses.application.ProductSearchResponseService;
import dev.julioperez.nls.productsearchresponses.domain.HumanizedProductSearchResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/product-search-responses")
@Tag(name = "Product search responses", description = "Turns structured product search facts into customer-facing replies.")
public class ProductSearchResponseController {
    private final ProductSearchResponseService responseService;

    public ProductSearchResponseController(ProductSearchResponseService responseService) {
        this.responseService = responseService;
    }

    @PostMapping("/humanize")
    @Operation(
            summary = "Humanize structured search results",
            description = "Creates a Spanish customer reply from the supplied product facts without querying the catalog.")
    public ProductSearchHumanizationResponse humanize(
            @Valid @RequestBody HumanizeProductSearchResponseRequest request) {
        HumanizedProductSearchResponse result = responseService.humanize(
                request.message(), request.results().toFacts());
        return new ProductSearchHumanizationResponse(result.outcome(), result.reply());
    }
}
