package com.tgaws.business.mon.manager;

import com.tgaws.common.store.SampleRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 第三方推送数据接收（T-702，FR-105）：
 * 校验（点位存在/时间格式/数值/质量位）→ data_sample 落库（source=5 第三方）。
 * 判定链路接入留二期（与文件导入口径一致，仅入库不判定）。
 */
@Service
public class OpenPushService {

    private static final Logger log = LoggerFactory.getLogger(OpenPushService.class);

    private static final ZoneId DB_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int SOURCE_THIRD = 5;

    private final SampleStoreManager sampleStoreManager;

    public OpenPushService(SampleStoreManager sampleStoreManager) {
        this.sampleStoreManager = sampleStoreManager;
    }

    /**
     * 处理推送批次。
     *
     * @param items 载荷 items[]（pointCode/ts/value/quality）
     * @return received=入库成功条数，failed=校验失败条数
     */
    public PushResult push(String batchNo, List<Map<String, Object>> items) {
        int failed = 0;
        List<SampleRow> rows = new ArrayList<>();
        for (Map<String, Object> item : items) {
            String pointCode = String.valueOf(item.get("pointCode"));
            Long pointId = sampleStoreManager.resolvePointId(pointCode);
            if (pointId == null) {
                log.warn("Open 推送点位不存在：{}（批次 {}）", pointCode, batchNo);
                failed++;
                continue;
            }
            long tsMs;
            try {
                LocalDateTime ts = LocalDateTime.parse(String.valueOf(item.get("ts")), TS_FMT);
                tsMs = ts.atZone(DB_ZONE).toInstant().toEpochMilli();
            } catch (DateTimeParseException e) {
                failed++;
                continue;
            }
            BigDecimal value;
            try {
                value = new BigDecimal(String.valueOf(item.get("value")));
            } catch (NumberFormatException e) {
                failed++;
                continue;
            }
            int quality = ((Number) item.getOrDefault("quality", 0)).intValue();
            if (quality < 0 || quality > 3) {
                failed++;
                continue;
            }
            rows.add(new SampleRow(pointId, tsMs, value, quality, SOURCE_THIRD));
        }
        int received = sampleStoreManager.batchInsert(rows);
        log.info("Open 推送批次 {}：接收 {} 失败 {}", batchNo, received, failed);
        return new PushResult(received, failed);
    }

    /** 推送结果 */
    public record PushResult(int received, int failed) {
    }
}
