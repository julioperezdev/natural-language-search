package dev.julioperez.nls.products.infrastructure.http;

import dev.julioperez.nls.products.application.ProductSearchService;
import dev.julioperez.nls.products.domain.search.Criteria;
import dev.julioperez.nls.products.domain.search.ProductSearchPage;
import dev.julioperez.nls.products.domain.search.SearchSchema;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products/search")
@Tag(name = "Product search", description = "Structured and natural-language product catalog search.")
public class ProductSearchController {
    private final ProductSearchService searchService;
    private final ProductSearchCriteriaAdapter criteriaAdapter;

    public ProductSearchController(
            ProductSearchService searchService,
            ProductSearchCriteriaAdapter criteriaAdapter) {
        this.searchService = searchService;
        this.criteriaAdapter = criteriaAdapter;
    }

    @GetMapping("/schema")
    @Operation(summary = "Get the search schema", description = "Returns the allowed semantic fields and filters.")
    public SearchSchema schema() {
        return searchService.schema();
    }

    @PostMapping
    @Operation(summary = "Search products", description = "Searches with structured criteria or a natural-language message.")
    public ProductSearchPage search(@Valid @RequestBody ProductSearchRequest request) {
        Criteria criteria = criteriaAdapter.toCriteria(request);
        if (criteria == null) {
            criteria = searchService.interpret(request.message());
        }
        return searchService.search(criteria);
    }

    @PostMapping("/interpret")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Interpret a message", description = "Converts a message into validated search criteria without querying products.")
    public SearchCriteriaResponse interpret(@Valid @RequestBody SearchInterpretRequest request) {
        return criteriaAdapter.toResponse(searchService.interpret(request.message()));
    }
}
