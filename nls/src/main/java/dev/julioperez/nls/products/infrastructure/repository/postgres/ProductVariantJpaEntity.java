package dev.julioperez.nls.products.infrastructure.repository.postgres;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "product_variants")
public class ProductVariantJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private ProductJpaEntity product;

    @Column(nullable = false, length = 80)
    private String color;

    @Column(nullable = false, length = 40)
    private String size;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal price;

    @Column(nullable = false)
    private Integer stock;

    protected ProductVariantJpaEntity() {
    }

    public ProductVariantJpaEntity(
            ProductJpaEntity product,
            String color,
            String size,
            BigDecimal price,
            Integer stock) {
        this.product = product;
        this.color = color;
        this.size = size;
        this.price = price;
        this.stock = stock;
    }
}
