package com.tgaws.web.log;

import com.tgaws.business.sys.log.OperLogRecorder;
import com.tgaws.common.result.ErrorCode;
import com.tgaws.common.result.Result;
import com.tgaws.web.security.LoginContext;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

/**
 * 操作审计切面（T-703，8.8 S-08）：
 * 拦截 @OperLog 注解方法 → 异步脱敏落 sys_oper_log；
 * 业务异常不吞（照常抛出），审计失败由 Recorder 降级。
 */
@Aspect
@Component
public class OperLogAspect {

    private static final Logger log = LoggerFactory.getLogger(OperLogAspect.class);

    private final OperLogRecorder recorder;

    public OperLogAspect(OperLogRecorder recorder) {
        this.recorder = recorder;
    }

    @Around("@annotation(operLog)")
    public Object around(ProceedingJoinPoint joinPoint, OperLog operLog) throws Throwable {
        long start = System.currentTimeMillis();
        String responseCode = "00000";
        try {
            Object result = joinPoint.proceed();
            if (result instanceof Result<?> r && r.getCode() != null) {
                responseCode = r.getCode();
            }
            return result;
        } catch (Throwable e) {
            responseCode = ErrorCode.D0005.getCode();
            throw e;
        } finally {
            record(joinPoint, operLog, responseCode, (int) (System.currentTimeMillis() - start));
        }
    }

    private void record(ProceedingJoinPoint joinPoint, OperLog operLog, String responseCode, int costMs) {
        try {
            LoginContext.LoginUser user = LoginContext.getUser();
            String ip = resolveIp();
            recorder.record(new OperLogRecorder.Entry(
                    user == null ? null : user.userId(),
                    user == null ? null : user.username(),
                    operLog.module(), operLog.value(),
                    joinPoint.getSignature().toShortString(),
                    OperLogRecorder.maskParams(joinPoint.getArgs()),
                    responseCode, costMs, ip, LocalDateTime.now()));
        } catch (Exception e) {
            log.error("审计切面异常（降级仅日志）：{}", joinPoint.getSignature().toShortString(), e);
        }
    }

    private String resolveIp() {
        try {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                return null;
            }
            HttpServletRequest request = attributes.getRequest();
            String forwarded = request.getHeader("X-Forwarded-For");
            return forwarded != null && !forwarded.isBlank()
                    ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
        } catch (Exception e) {
            return null;
        }
    }
}
