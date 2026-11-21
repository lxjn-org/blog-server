package com.lxjn.blog.system;

import com.lxjn.blog.web.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {
    private final DatabaseReadiness database;

    /** 注入统一数据库就绪检查。 */
    public HealthController(DatabaseReadiness database) {
        this.database = database;
    }

    /** 通过真实查询验证数据库已初始化，并按管理接口约定返回结果。 */
    @GetMapping("/health")
    public ApiResponse<Map<String, Boolean>> health() {
        database.requireReady();
        return ApiResponse.ok(Map.of("ready", true));
    }
}
