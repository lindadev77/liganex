package tech.liganex.studio.module.order.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 跨境订单（分区表，ADR-0004：RANGE(created_at) + LIST(region)）。
 *
 * <p>查询条件须带上分区键（时间、地区）以命中分区裁剪。
 */
@Data
@TableName("customer_order")
public class Order {

    private Long id;
    private String orderNo;
    private String region;
    private String status;
    private BigDecimal amount;
    private String currency;
    private String buyerName;
    private Instant createdAt;
}
