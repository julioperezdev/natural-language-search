package dev.julioperez.nls.products.application;

import dev.julioperez.nls.products.domain.search.Criteria;
import dev.julioperez.nls.products.domain.search.ProductSearchPage;
import dev.julioperez.nls.products.domain.search.ProductSearchRepository;
import dev.julioperez.nls.products.domain.search.SearchSchema;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class ProductSearchService {
    private final ProductSearchRepository repository;
    private final Optional<SearchDecisionEngine> decisionEngine;

    public ProductSearchService(
            ProductSearchRepository repository,
            Optional<SearchDecisionEngine> decisionEngine) {
        this.repository = repository;
        this.decisionEngine = decisionEngine;
    }

    public ProductSearchPage search(Criteria criteria) {
        return repository.search(criteria);
    }

    public Criteria validate(Criteria criteria) {
        return repository.validate(criteria);
    }

    public Criteria interpret(String message) {
        SearchDecisionEngine engine = decisionEngine.orElseThrow(SearchInterpretationUnavailableException::new);
        Criteria interpreted = engine.interpret(message, repository.schema());
        return repository.validate(interpreted);
    }

    public Criteria interpret(String message, Criteria current) {
        SearchDecisionEngine engine = decisionEngine.orElseThrow(SearchInterpretationUnavailableException::new);
        Criteria interpreted = engine.interpretTurn(message, repository.schema(), current);
        return repository.validate(interpreted);
    }

    public SearchSchema schema() {
        return repository.schema();
    }
}
