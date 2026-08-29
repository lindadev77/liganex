package tech.liganex.studio.module.mcp.security;

import java.util.List;

/**
 * 通过 MCP 鉴权后的应用上下文，供后续工具执行使用。
 */
public record McpAuthContext(String appId, List<String> scopes) {
}
