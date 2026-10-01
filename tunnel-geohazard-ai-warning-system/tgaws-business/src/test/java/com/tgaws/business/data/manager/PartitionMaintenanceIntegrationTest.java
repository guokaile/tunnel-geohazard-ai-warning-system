package com.tgaws.business.data.manager;

import com.tgaws.business.SampleStoreTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 分区维护集成测试（T-709：tgaws_test 真实执行 REORGANIZE/巡检，需 DB 凭据注入；
 * 建分区幂等——重复执行零副作用，pmax 永远兜底）。
 */
@SpringBootTest(classes = SampleStoreTestConfig.class)
@ActiveProfiles("test")
class PartitionMaintenanceIntegrationTest {

    @Autowired
    private PartitionMaintenanceService partitionMaintenanceService;

    @Test
    void ensureFuturePartitionsIsIdempotentOnRealDb() {
        int first = partitionMaintenanceService.ensureFuturePartitions();
        int second = partitionMaintenanceService.ensureFuturePartitions();
        assertTrue(second == 0, "重复执行幂等（第二轮 created=0），实际 " + second);
        // 巡检：pmax 兜底存在、边界余量充足、pmax 无遗留数据
        PartitionMaintenanceService.CheckResult check = partitionMaintenanceService.dailyCheck();
        assertTrue(check.healthy(), "分区巡检应健康，实际：" + check.problems());
    }
}
