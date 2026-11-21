package com.lxjn.blog.system;

import com.lxjn.blog.web.ApiException;
import com.lxjn.blog.web.ErrorCode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.Locale;
import java.util.Map;

@Component
public class DatabaseReadiness {
    private final ObjectProvider<ComponentVersionMapper> versions;

    /** 延迟获取 Mapper，使未配置数据源时也能启动管理服务。 */
    public DatabaseReadiness(ObjectProvider<ComponentVersionMapper> versions) {
        this.versions = versions;
    }

    /** 区分首次初始化和已配置数据库的连接故障。 */
    public void requireReady() {
        var mapper = versions.getIfAvailable();
        if (mapper == null) throw failure(ErrorCode.DATABASE_NOT_INITIALIZED);
        Integer version;
        try {
            version = mapper.findDatabaseVersion();
        } catch (DataAccessException exception) {
            Throwable cause = exception.getMostSpecificCause();
            if (cause instanceof SQLException sql && missingVersionTable(sql)) {
                throw failure(ErrorCode.DATABASE_NOT_INITIALIZED);
            }
            throw failure(ErrorCode.SERVICE_UNAVAILABLE);
        }
        if (version == null || version <= 0) throw failure(ErrorCode.DATABASE_NOT_INITIALIZED);
    }

    /** 识别 MySQL 和 SQLite 报告的版本表不存在。 */
    private static boolean missingVersionTable(SQLException exception) {
        return "42S02".equals(exception.getSQLState())
                || String.valueOf(exception.getMessage()).toLowerCase(Locale.ROOT)
                        .contains("no such table: component_version");
    }

    /** 为数据库检查错误附加稳定的就绪标记。 */
    private static ApiException failure(ErrorCode code) {
        return new ApiException(code, code.message(), Map.of("ready", false));
    }
}
