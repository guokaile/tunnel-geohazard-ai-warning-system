package com.tgaws.web.config;

import com.tgaws.common.exception.BizException;
import com.tgaws.common.exception.SysException;
import com.tgaws.common.result.ErrorCode;
import com.tgaws.common.result.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器（《6.详细设计说明书》6.6 异常与错误处理设计）。
 *
 * <p>BizException 返回业务码（不打印堆栈）；参数校验失败映射 A0001/A0002；
 * SysException/未知异常 ERROR 留痕，对外返回脱敏错误码。</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 业务异常：可预期，返回对应错误码 */
    @ExceptionHandler(BizException.class)
    public Result<Void> handleBiz(BizException e) {
        return Result.fail(e.getErrorCode(), e.getMessage());
    }

    /** 参数校验异常（@Valid 注解触发） */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().isEmpty()
                ? ErrorCode.A0002.getMessage()
                : e.getBindingResult().getFieldErrors().get(0).getDefaultMessage();
        return Result.fail(ErrorCode.A0002, msg);
    }

    /** 系统异常：ERROR 留痕，对外脱敏 */
    @ExceptionHandler(SysException.class)
    public Result<Void> handleSys(SysException e) {
        log.error("系统异常 code={}", e.getErrorCode().getCode(), e);
        return Result.fail(e.getErrorCode());
    }

    /** 未知异常：兜底 D0005 */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleUnknown(Exception e) {
        log.error("未知异常", e);
        return Result.fail(ErrorCode.D0005);
    }
}
