package tech.liganex.studio.module.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import tech.liganex.studio.common.PageResult;
import tech.liganex.studio.module.order.dto.OrderDTO;
import tech.liganex.studio.module.order.dto.OrderQueryRequest;
import tech.liganex.studio.module.order.entity.Order;
import tech.liganex.studio.module.order.mapper.OrderMapper;

import java.util.List;

/**
 * 订单查询领域服务。查询条件中的地区与时间范围即为分区键，用于命中分区裁剪（ADR-0004）。
 */
@Service
@RequiredArgsConstructor
public class OrderQueryService {

    private final OrderMapper orderMapper;

    public PageResult<OrderDTO> query(OrderQueryRequest request) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<Order>()
                .eq(StringUtils.hasText(request.region()), Order::getRegion, request.region())
                .eq(StringUtils.hasText(request.status()), Order::getStatus, request.status())
                .ge(request.from() != null, Order::getCreatedAt, request.from())
                .le(request.to() != null, Order::getCreatedAt, request.to())
                .orderByDesc(Order::getCreatedAt);

        IPage<Order> page = orderMapper.selectPage(
                new Page<>(request.page(), request.size()), wrapper);

        List<OrderDTO> records = page.getRecords().stream().map(OrderDTO::from).toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }
}
