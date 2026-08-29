package tech.liganex.studio.module.order.dto;

import java.math.BigDecimal;

/**
 * 订单写入入参（MCP order:write 工具传入）。
 */
public record OrderWriteRequest(String region, String status, BigDecimal amount,
                                String currency, String buyerName) {
}
