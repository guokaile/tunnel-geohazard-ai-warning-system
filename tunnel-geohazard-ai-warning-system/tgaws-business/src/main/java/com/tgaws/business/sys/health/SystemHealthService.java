package com.tgaws.business.sys.health;

import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * 系统健康检查服务（W8：安装向导第 8 步"健康检查接口"验证、运维探活共用）。
 * 口径：应用存活 + 数据库连通（connection.isValid，2s 判定），不触碰业务表；
 * 分层约束：仅依赖 DataSource，不依赖 mapper（ArchUnit 守护范围外）。
 */
@Service
public class SystemHealthService {

    private final DataSource dataSource;

    public SystemHealthService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * 数据库连通性检查。
     *
     * @return true=连通；false=连接失败或判定超时
     */
    public boolean checkDb() {
        try (Connection conn = dataSource.getConnection()) {
            return conn.isValid(2);
        } catch (SQLException e) {
            return false;
        }
    }
}
