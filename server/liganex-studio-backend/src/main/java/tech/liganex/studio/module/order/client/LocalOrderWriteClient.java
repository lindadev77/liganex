package tech.liganex.studio.module.order.client;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tech.liganex.studio.common.BizException;
import tech.liganex.studio.common.ErrorCode;
import tech.liganex.studio.module.order.dto.OrderDTO;
import tech.liganex.studio.module.order.dto.OrderWriteRequest;
import tech.liganex.studio.module.order.entity.Order;
import tech.liganex.studio.module.order.entity.Shipment;
import tech.liganex.studio.module.order.mapper.OrderMapper;
import tech.liganex.studio.module.order.mapper.ShipmentMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * 本地实现：同进程内直连领域 Mapper 写入（当前默认，ADR-0009 第一阶段）。
 *
 * <p>分区键 created_at 取当前时间，落在 V3 迁移已建的分区范围内（2026-08/09）。
 */
@Component
@ConditionalOnProperty(name = "liganex.order.client", havingValue = "local", matchIfMissing = true)
@RequiredArgsConstructor
public class LocalOrderWriteClient implements OrderWriteClient {

    private final OrderMapper orderMapper;
    private final ShipmentMapper shipmentMapper;

    @Override
    public String create(OrderWriteRequest request) {
        String orderNo = "LNX-" + LocalDate.now() + "-" +
                UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        Order order = new Order();
        order.setOrderNo(orderNo);
        order.setRegion(request.region());
        order.setStatus(request.status());
        order.setAmount(request.amount());
        order.setCurrency(request.currency());
        order.setBuyerName(request.buyerName());
        order.setCreatedAt(Instant.now());
        orderMapper.insert(order);
        return orderNo;
    }

    @Override
    public OrderDTO updateStatus(String orderNo, String status) {
        Order order = requireOrder(orderNo);
        order.setStatus(status);
        orderMapper.updateById(order);
        return OrderDTO.from(order);
    }

    @Override
    @Transactional
    public OrderDTO ship(String orderNo, String carrier, String trackingNo) {
        Order order = requireOrder(orderNo);

        Shipment shipment = new Shipment();
        shipment.setOrderNo(orderNo);
        shipment.setRegion(order.getRegion());
        shipment.setCarrier(carrier);
        shipment.setTrackingNo(trackingNo);
        shipment.setShippedAt(Instant.now());
        shipment.setCreatedAt(Instant.now());
        shipmentMapper.insert(shipment);

        order.setStatus("SHIPPED");
        orderMapper.updateById(order);
        return OrderDTO.from(order);
    }

    private Order requireOrder(String orderNo) {
        Order order = orderMapper.selectOne(
                new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, orderNo));
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }
        return order;
    }
}
