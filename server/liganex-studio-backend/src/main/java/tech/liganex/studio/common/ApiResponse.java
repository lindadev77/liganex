package tech.liganex.studio.common;

import java.time.Instant;

/**
 * 统一 API 响应包装。
 *
 * @param code    0 表示成功，非 0 为业务错误码
 * @param message 可读提示，失败时不泄露内部细节
 * @param data    业务数据
 * @param time    响应时间戳
 */
public record ApiResponse<T>(int code, String message, T data, Instant time) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "OK", data, Instant.now());
    }

    public static <T> ApiResponse<T> fail(ErrorCode errorCode) {
        return new ApiResponse<>(errorCode.code(), errorCode.message(), null, Instant.now());
    }

    public static <T> ApiResponse<T> fail(ErrorCode errorCode, String detail) {
        return new ApiResponse<>(errorCode.code(), detail, null, Instant.now());
    }
}
