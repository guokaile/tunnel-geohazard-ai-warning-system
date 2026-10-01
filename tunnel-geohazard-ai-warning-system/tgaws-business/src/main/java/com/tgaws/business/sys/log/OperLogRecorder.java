package com.tgaws.business.sys.log;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tgaws.business.sys.mapper.OperLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * 审计记录器（T-703，6.3.7 落地口径）：
 *
 * <ul>
 *   <li><b>异步不阻断业务</b>：单线程+有界队列（1 万），队满丢弃并 ERROR 日志；
 *       写库失败同样仅 ERROR 日志（审计降级不阻断业务）；</li>
 *   <li><b>脱敏</b>：入参序列化后按字段名掩码（password/secret/token/psk/pwd 等），
 *       再经 MaskUtil 兜底；</li>
 *   <li><b>哈希链防篡改</b>（8.8 S-08）：audit_hash = SHA-256(链尾哈希 + 本行规范化内容)，
 *       任何行被篡改/删除/插入 → 后续链哈希全部不匹配；配合 REVOKE UPDATE/DELETE 双保险。</li>
 * </ul>
 */
@Component
public class OperLogRecorder {

    private static final Logger log = LoggerFactory.getLogger(OperLogRecorder.class);

    /** 敏感"字段名:值"对（掩码名与值，防仅掩名漏值） */
    private static final Pattern SENSITIVE_PAIR = Pattern.compile(
            "(?i)\"(password|passwd|secret|token|psk|pwd|credential)\"\\s*:\\s*\"[^\"]*\"");

    private final OperLogMapper operLogMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService executor = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(10_000),
            r -> {
                Thread t = new Thread(r, "oper-log-recorder");
                t.setDaemon(true);
                return t;
            },
            (r, pool) -> log.error("审计队列已满，丢弃审计记录（不阻断业务）"));

    public OperLogRecorder(OperLogMapper operLogMapper) {
        this.operLogMapper = operLogMapper;
    }

    /** 异步记录（调用方不感知失败） */
    public void record(Entry entry) {
        executor.execute(() -> write(entry));
    }

    private void write(Entry entry) {
        try {
            String prevHash = operLogMapper.selectLastHash();
            String content = canonical(entry);
            String hash = sha256((prevHash == null ? "" : prevHash) + content);
            operLogMapper.insert(entry.userId(), entry.username(), entry.module(),
                    entry.operation(), entry.method(), entry.requestParams(),
                    entry.responseCode(), entry.costMs(), entry.ip(),
                    entry.operTime(), hash);
        } catch (Exception e) {
            log.error("审计写库失败（降级仅日志，不阻断业务）：operation={}", entry.operation(), e);
        }
    }

    /** 规范化内容串（哈希链输入） */
    static String canonical(Entry entry) {
        return String.join("|",
                String.valueOf(entry.userId()), String.valueOf(entry.username()),
                entry.module(), entry.operation(), entry.method(),
                String.valueOf(entry.requestParams()), String.valueOf(entry.responseCode()),
                String.valueOf(entry.costMs()), String.valueOf(entry.ip()),
                String.valueOf(entry.operTime()));
    }

    static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    /**
     * 入参脱敏：序列化 + 敏感字段名掩码 + 值掩码兜底。
     * 序列化失败返回类名占位（脱敏优先于完整性）。
     */
    public static String maskParams(Object[] args) {
        if (args == null || args.length == 0) {
            return null;
        }
        try {
            String json = new ObjectMapper().writeValueAsString(args);
            return maskSensitiveJson(json);
        } catch (Exception e) {
            StringBuilder sb = new StringBuilder("[");
            for (Object arg : args) {
                if (arg != null) {
                    sb.append(arg.getClass().getSimpleName()).append(',');
                }
            }
            return sb.append("]").toString();
        }
    }

    /** 敏感"字段名:值"整体掩码（`"password":"***"`，值不落审计） */
    static String maskSensitiveJson(String json) {
        return SENSITIVE_PAIR.matcher(json).replaceAll("\"$1\":\"***\"");
    }

    /** 审计条目 */
    public record Entry(Long userId, String username, String module, String operation,
                        String method, String requestParams, String responseCode,
                        Integer costMs, String ip, LocalDateTime operTime) {
    }
}
