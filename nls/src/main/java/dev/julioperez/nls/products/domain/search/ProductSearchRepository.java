package dev.julioperez.nls.products.domain.search;

public interface ProductSearchRepository {
    ProductSearchPage search(Criteria criteria);

    Criteria validate(Criteria criteria);

    SearchSchema schema();
}
