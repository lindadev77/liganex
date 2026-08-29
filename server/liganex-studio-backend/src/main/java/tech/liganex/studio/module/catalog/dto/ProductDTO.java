package tech.liganex.studio.module.catalog.dto;

import tech.liganex.studio.module.catalog.entity.Product;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 商品出参 DTO（模块边界：对外只暴露 DTO，不外泄持久化实体）。
 */
public record ProductDTO(String sku, String name, String region, BigDecimal price,
                         String currency, Integer stock, Instant createdAt) {

    public static ProductDTO from(Product p) {
        return new ProductDTO(
                p.getSku(), p.getName(), p.getRegion(), p.getPrice(),
                p.getCurrency(), p.getStock(), p.getCreatedAt());
    }
}
