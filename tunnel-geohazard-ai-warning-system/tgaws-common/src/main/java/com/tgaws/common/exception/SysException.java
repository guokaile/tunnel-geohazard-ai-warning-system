package com.tgaws.common.exception;

import com.tgaws.common.result.ErrorCode;

/**
 * 系统异常（不可预期，ERROR 日志留痕，对外返回脱敏错误码）。
 */
public class SysException extends RuntimeException {

    private final ErrorCode errorCode;

    public SysException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public SysException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
