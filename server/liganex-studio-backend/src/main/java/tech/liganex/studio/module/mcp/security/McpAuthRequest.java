package tech.liganex.studio.module.mcp.security;

/**
 * 一次 MCP 调用的鉴权材料（来自请求头 + 原始请求体 + 所需权限）。
 *
 * <p>signature 为对 {@code canonical = method + "\n" + path + "\n" + timestamp + "\n" + nonce + "\n" + body}
 * 做 HMAC-SHA256 后的十六进制串，密钥为应用的明文 appsecret。
 */
public record McpAuthRequest(String appId,
                             String method,
                             String path,
                             String timestamp,
                             String nonce,
                             String signature,
                             String body,
                             String requiredScope) {
}
