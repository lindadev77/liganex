package tech.liganex.studio.module.catalog.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 商品主数据（开放平台 product:read 工具的真实数据源）。
 */
@Data
@TableName("product")
public class Product {

    private Long id;
    private String sku;
    private String name;
    private String region;
    private BigDecimal price;
    private String currency;
    private Integer stock;
    private Instant createdAt;
}
