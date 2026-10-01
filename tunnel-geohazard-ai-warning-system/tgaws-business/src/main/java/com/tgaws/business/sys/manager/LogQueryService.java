package com.tgaws.business.sys.manager;

import com.tgaws.business.sys.mapper.LogQueryMapper;
import com.tgaws.common.result.PageResult;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 审计日志查询服务（T-813：A22 操作日志/A23 登录日志，只读；
 * 防篡改哈希链 audit_hash 列不返回，篡改校验由阶段8测试/运维工具离线执行）。
 */
@Service
public class LogQueryService {

    private final LogQueryMapper logQueryMapper;

    public LogQueryService(LogQueryMapper logQueryMapper) {
        this.logQueryMapper = logQueryMapper;
    }

    /** A22 操作日志分页 */
    public PageResult<Map<String, Object>> operPage(String username, String module,
                                                    LocalDateTime from, LocalDateTime to,
                                                    int pageNum, int pageSize) {
        long total = logQueryMapper.countOperPage(username, module, from, to);
        if (total == 0) {
            return new PageResult<>(0L, List.of());
        }
        return new PageResult<>(total, logQueryMapper.selectOperPage(username, module, from, to,
                (pageNum - 1) * pageSize, pageSize));
    }

    /** A23 登录日志分页 */
    public PageResult<Map<String, Object>> loginPage(String username,
                                                     LocalDateTime from, LocalDateTime to,
                                                     int pageNum, int pageSize) {
        long total = logQueryMapper.countLoginPage(username, from, to);
        if (total == 0) {
            return new PageResult<>(0L, List.of());
        }
        return new PageResult<>(total, logQueryMapper.selectLoginPage(username, from, to,
                (pageNum - 1) * pageSize, pageSize));
    }
}
