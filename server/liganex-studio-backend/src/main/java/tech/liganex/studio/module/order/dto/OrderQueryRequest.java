package tech.liganex.studio.module.order.dto;

import java.time.Instant;

/**
 * 订单查询条件。region / 时间范围是分区键，带上才能命中分区裁剪（ADR-0004）。
 */
public record OrderQueryRequest(String region, String status, Instant from, Instant to, long page, long size) {

    private static final long DEFAULT_SIZE = 20;
    private static final long MAX_SIZE = 200;

    public OrderQueryRequest {
        if (page < 1) {
            page = 1;
        }
        if (size < 1 || size > MAX_SIZE) {
            size = DEFAULT_SIZE;
        }
    }
}
