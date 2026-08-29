package tech.liganex.studio.module.openapp.dto;

/**
 * 创建应用时一次性返回的明文 appsecret。请调用方妥善保存，库内仅存密文（ADR-0007）。
 */
public record AppSecretResponse(String appId, String appSecret) {
}
