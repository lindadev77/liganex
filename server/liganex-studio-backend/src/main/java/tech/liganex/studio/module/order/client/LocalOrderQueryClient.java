package tech.liganex.studio.module.order.client;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tech.liganex.studio.common.PageResult;
import tech.liganex.studio.module.order.dto.OrderDTO;
import tech.liganex.studio.module.order.dto.OrderQueryRequest;
import tech.liganex.studio.module.order.service.OrderQueryService;

/**
 * 本地实现：同进程内直连领域服务（当前默认，ADR-0009 第一阶段）。
 */
@Component
@ConditionalOnProperty(name = "liganex.order.client", havingValue = "local", matchIfMissing = true)
@RequiredArgsConstructor
public class LocalOrderQueryClient implements OrderQueryClient {

    private final OrderQueryService orderQueryService;

    @Override
    public PageResult<OrderDTO> query(OrderQueryRequest request) {
        return orderQueryService.query(request);
    }
}
