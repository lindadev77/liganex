package tech.liganex.studio.module.order.client;

import tech.liganex.studio.module.order.dto.OrderDTO;
import tech.liganex.studio.module.order.dto.OrderWriteRequest;

/**
 * 订单写入的消费方契约（ADR-0009 服务化就绪的关键抽象）。
 *
 * <p>消费方（MCP tool）只依赖本接口；本地实现与远程实现可由配置切换，
 * 将来把订单拆为独立服务时消费方代码零改动（严禁 MCP 直连订单库）。
 */
public interface OrderWriteClient {

    /**
     * 创建一条订单，返回生成的订单号。
     */
    String create(OrderWriteRequest request);

    /**
     * 更新订单状态（订单状态机流转：PENDING → PAID → SHIPPED → DELIVERED / CANCELLED）。
     * 返回更新后的订单。
     */
    OrderDTO updateStatus(String orderNo, String status);

    /**
     * 发货：写入物流运单并把订单置为 SHIPPED。返回更新后的订单。
     */
    OrderDTO ship(String orderNo, String carrier, String trackingNo);
}
