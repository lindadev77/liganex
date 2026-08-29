package tech.liganex.studio.module.catalog.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * 分仓库存（开放平台 inventory:read 工具的真实数据源）。
 */
@Data
@TableName("inventory")
public class Inventory {

    private Long id;
    private String sku;
    private String region;
    private String warehouse;
    private Integer availableQty;
    private Integer lockedQty;
    private Instant updatedAt;
}
