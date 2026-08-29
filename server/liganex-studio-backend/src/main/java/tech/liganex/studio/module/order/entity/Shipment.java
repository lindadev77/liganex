package tech.liganex.studio.module.order.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * 物流运单（发货环节，由 order_ship 工具写入）。
 */
@Data
@TableName("shipment")
public class Shipment {

    private Long id;
    private String orderNo;
    private String region;
    private String carrier;
    private String trackingNo;
    private Instant shippedAt;
    private Instant createdAt;
}
