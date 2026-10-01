package com.tgaws.web.config;

import com.tgaws.business.data.manager.PartitionMaintenanceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 分区维护调度（T-709，NFR-DATA1/《3》3.5.1）：
 * 每月 1 日 00:10 建未来分区（REORGANIZE pmax，幂等）+ 03:30 过期删除（保留 1 年）；
 * 每日 01:20 巡检（pmax 兜底/边界余量/pmax 遗留数据告警）。
 */
@Component
public class PartitionMaintenanceScheduler {

    private static final Logger log = LoggerFactory.getLogger(PartitionMaintenanceScheduler.class);

    private final PartitionMaintenanceService partitionMaintenanceService;

    public PartitionMaintenanceScheduler(PartitionMaintenanceService partitionMaintenanceService) {
        this.partitionMaintenanceService = partitionMaintenanceService;
    }

    @Scheduled(cron = "0 10 0 1 * ?", zone = "Asia/Shanghai")
    public void monthlyCreate() {
        try {
            int created = partitionMaintenanceService.ensureFuturePartitions();
            log.info("分区月度维护完成：新建 {} 个分区", created);
        } catch (Exception e) {
            // D0007 口径：建分区失败数据落 pmax 不丢，巡检告警人工介入
            log.error("D0007 建分区任务失败（数据落 pmax 兜底，巡检将告警）", e);
        }
    }

    @Scheduled(cron = "0 30 3 1 * ?", zone = "Asia/Shanghai")
    public void monthlyDrop() {
        try {
            int dropped = partitionMaintenanceService.dropExpiredPartitions();
            log.info("分区过期删除完成：{} 个分区", dropped);
        } catch (Exception e) {
            log.error("分区过期删除失败（下月重试，幂等）", e);
        }
    }

    @Scheduled(cron = "0 20 1 * * ?", zone = "Asia/Shanghai")
    public void dailyCheck() {
        try {
            PartitionMaintenanceService.CheckResult result = partitionMaintenanceService.dailyCheck();
            if (!result.healthy()) {
                log.error("D0008 分区健康异常：{}", result.problems());
            }
        } catch (Exception e) {
            log.error("分区巡检失败", e);
        }
    }
}
