package tech.liganex.studio.common;

/**
 * 业务异常：携带 {@link ErrorCode}，由 {@link GlobalExceptionHandler} 转为统一响应。
 * 抛出时应避免把敏感信息（口令、令牌、SQL）拼进 message。
 */
public class BizException extends RuntimeException {

    private final ErrorCode errorCode;

    public BizException(ErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }

    public BizException(ErrorCode errorCode, String detail) {
        super(detail);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
