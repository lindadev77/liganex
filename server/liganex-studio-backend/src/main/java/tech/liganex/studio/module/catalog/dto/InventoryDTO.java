package tech.liganex.studio.module.catalog.dto;

import tech.liganex.studio.module.catalog.entity.Inventory;

import java.time.Instant;

/**
 * 库存出参 DTO（模块边界：对外只暴露 DTO，不外泄持久化实体）。
 */
public record InventoryDTO(String sku, String region, String warehouse,
                           Integer availableQty, Integer lockedQty, Instant updatedAt) {

    public static InventoryDTO from(Inventory i) {
        return new InventoryDTO(
                i.getSku(), i.getRegion(), i.getWarehouse(),
                i.getAvailableQty(), i.getLockedQty(), i.getUpdatedAt());
    }
}
