package com.tgaws.business.ai.manager;

import com.tgaws.business.ai.entity.MinuteRow;
import com.tgaws.business.ai.mapper.MinuteQueryMapper;
import com.tgaws.common.rule.PointSample;
import com.tgaws.common.store.ISampleWindowProvider;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 分钟窗口提供实现（ISampleWindowProvider 契约 → data_sample_minute 查询）。
 *
 * <p>说明：预测批算窗口大小由 web 调度器传入（当前 180 分钟接线验证；
 * 长窗口降采样优化列入 W6）。</p>
 */
@Component
public class MinuteWindowProvider implements ISampleWindowProvider {

    private static final ZoneId DB_ZONE = ZoneId.of("Asia/Shanghai");

    private final MinuteQueryMapper minuteMapper;

    public MinuteWindowProvider(MinuteQueryMapper minuteMapper) {
        this.minuteMapper = minuteMapper;
    }

    @Override
    public Map<Long, List<PointSample>> minuteWindowForAllPoints(int windowMinutes) {
        LocalDateTime fromTs = LocalDateTime.now(DB_ZONE).minusMinutes(windowMinutes);
        List<MinuteRow> rows = minuteMapper.selectWindowForAllPoints(fromTs);
        Map<Long, List<PointSample>> windows = new LinkedHashMap<>();
        for (MinuteRow row : rows) {
            windows.computeIfAbsent(row.getPointId(), k -> new ArrayList<>())
                    .add(new PointSample(
                            row.getTsMinute().atZone(DB_ZONE).toInstant().toEpochMilli(),
                            row.getAvgValue(), 0));
        }
        return windows;
    }
}
