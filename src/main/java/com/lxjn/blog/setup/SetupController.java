package com.lxjn.blog.setup;

import com.lxjn.blog.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/setup")
public class SetupController {
    private final SetupService setup;

    /** 注入首装业务逻辑，供三个配置入口共用。 */
    public SetupController(SetupService setup) {
        this.setup = setup;
    }

    /** 测试 SQLite 或 MySQL 的连接。 */
    @PostMapping("/database/test")
    public ApiResponse<Map<String, Boolean>> testDatabase(@Valid @RequestBody SetupRequest.DatabaseTest request) {
        setup.testDatabase(request);
        return ApiResponse.ok(Map.of("connected", true));
    }

    /** 测试 Redis 缓存的连接。 */
    @PostMapping("/redis/test")
    public ApiResponse<Map<String, Boolean>> testRedis(@Valid @RequestBody SetupRequest.RedisTest request) {
        setup.testRedis(request);
        return ApiResponse.ok(Map.of("connected", true));
    }

    /** 完成安装并返回需要重启的状态。 */
    @PostMapping
    public ApiResponse<Map<String, Boolean>> save(@Valid @RequestBody SetupRequest.Save request) {
        setup.save(request);
        return ApiResponse.ok(Map.of("restartRequired", true));
    }
}
