package tech.liganex.studio.module.order.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tech.liganex.studio.common.ApiResponse;
import tech.liganex.studio.common.PageResult;
import tech.liganex.studio.module.order.client.OrderQueryClient;
import tech.liganex.studio.module.order.dto.OrderDTO;
import tech.liganex.studio.module.order.dto.OrderQueryRequest;

import java.time.Instant;

/**
 * 内部服务订单查询接口（服务间凭证鉴权，/internal/** 链路）。
 *
 * <p>与 {@link OrderController}（用户 JWT）共用 {@link OrderQueryClient} 契约与 DTO，
 * 但鉴权体系不同：此处由 {@code InternalApiKeyFilter} 校验服务间 API Key（ADR-0009 服务化就绪）。
 * 将来订单拆为独立服务时，消费方改走远程实现即可，此端点即成为跨服务调用的入口。
 */
@RestController
@RequestMapping("/internal/v1/orders")
@RequiredArgsConstructor
public class InternalOrderController {

    private final OrderQueryClient orderQueryClient;

    @GetMapping
    public ApiResponse<PageResult<OrderDTO>> query(
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {

        return ApiResponse.ok(orderQueryClient.query(
                new OrderQueryRequest(region, status, from, to, page, size)));
    }
}
