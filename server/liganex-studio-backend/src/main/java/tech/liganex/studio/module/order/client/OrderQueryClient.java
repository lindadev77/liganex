package tech.liganex.studio.module.order.client;

import tech.liganex.studio.common.PageResult;
import tech.liganex.studio.module.order.dto.OrderDTO;
import tech.liganex.studio.module.order.dto.OrderQueryRequest;

/**
 * 订单查询的消费方契约（ADR-0009 服务化就绪的关键抽象）。
 *
 * <p>消费方（控制器、未来的 MCP tool）只依赖本接口；
 * 本地实现与远程实现可由配置切换，将来把订单拆为独立服务时消费方代码零改动。
 */
public interface OrderQueryClient {

    PageResult<OrderDTO> query(OrderQueryRequest request);
}
