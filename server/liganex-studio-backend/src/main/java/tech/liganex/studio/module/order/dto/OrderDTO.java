package tech.liganex.studio.module.order.dto;

import tech.liganex.studio.module.order.entity.Order;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 订单出参 DTO。模块边界：对外只暴露 DTO，不外泄持久化实体（ADR-0009）。
 */
public record OrderDTO(Long id,
                       String orderNo,
                       String region,
                       String status,
                       BigDecimal amount,
                       String currency,
                       String buyerName,
                       Instant createdAt) {

    public static OrderDTO from(Order order) {
        return new OrderDTO(
                order.getId(),
                order.getOrderNo(),
                order.getRegion(),
                order.getStatus(),
                order.getAmount(),
                order.getCurrency(),
                order.getBuyerName(),
                order.getCreatedAt());
    }
}
