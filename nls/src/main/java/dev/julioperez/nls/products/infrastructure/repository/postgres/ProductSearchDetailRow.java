package dev.julioperez.nls.products.infrastructure.repository.postgres;

import java.util.UUID;

public record ProductSearchDetailRow(UUID productId, String productName, String category) {
}
