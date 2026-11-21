package com.lxjn.blog.setup;

import com.lxjn.blog.system.DatabaseReadiness;
import com.lxjn.blog.web.ApiException;
import com.lxjn.blog.web.ErrorCode;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisException;
import io.lettuce.core.RedisURI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Properties;

@Service
public class SetupService {
    private static final Logger log = LoggerFactory.getLogger(SetupService.class);
    private final DatabaseReadiness readiness;
    private final String username;
    private final String password;
    private final Path config;

    /** 载入安装凭据及外置配置文件路径。 */
    public SetupService(DatabaseReadiness readiness,
                        @Value("${blog.setup.username}") String username,
                        @Value("${blog.setup.password}") String password,
                        @Value("${BLOG_SETUP_CONFIG:./data/setup.properties}") String config) {
        this.readiness = readiness;
        this.username = username;
        this.password = password;
        this.config = Path.of(config).toAbsolutePath().normalize();
    }

    /** 验证首装身份后独立测试用户填写的业务数据库。 */
    public void testDatabase(SetupRequest.DatabaseTest request) {
        authorize(request.admin());
        testDatabase(SetupDatabase.from(request.database()));
    }

    /** 验证首装身份后独立测试启用的 Redis。 */
    public void testRedis(SetupRequest.RedisTest request) {
        authorize(request.admin());
        if (!request.redis().enabled()) throw invalid("请先启用 Redis");
        testRedis(request.redis());
    }

    /** 保存前重新测试连接，初始化或复用数据库，最后以原子移动发布外置配置。 */
    public synchronized void save(SetupRequest.Save request) {
        authorize(request.admin());
        SetupDatabase database = SetupDatabase.from(request.database());
        if (config.equals(database.path())) throw invalid("数据库文件不能与配置文件使用同一个路径");
        testDatabase(database);
        Properties properties = new Properties();
        database.write(properties);
        writeRedis(request.redis(), properties);
        Path staged = null;
        try {
            Files.createDirectories(config.getParent());
            staged = Files.createTempFile(config.getParent(), ".setup-", ".properties");
            try (var output = Files.newOutputStream(staged)) {
                properties.store(output, "Blog server setup");
            }
            database.prepare(request.admin());
            Files.move(staged, config, StandardCopyOption.ATOMIC_MOVE);
        } catch (SQLException | IOException | GeneralSecurityException | org.springframework.jdbc.datasource.init.ScriptException exception) {
            log.warn("首装保存失败，异常类型 {}", exception.getClass().getSimpleName());
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "保存失败，请检查数据库结构、配置目录权限和服务日志", null);
        } finally {
            removeStagedFile(staged);
        }
    }

    /** 仅允许尚未配置或初始化的服务使用首装接口。 */
    private void authorize(SetupRequest.Credentials credentials) {
        if (password.length() < 12) {
            throw new ApiException(ErrorCode.FORBIDDEN, "请先为安装设置至少 12 个字符的初始管理员密码", null);
        }
        boolean userMatches = matches(username, credentials.username());
        boolean passwordMatches = matches(password, credentials.password());
        if (!userMatches || !passwordMatches) throw new ApiException(ErrorCode.UNAUTHORIZED);
        if (Files.exists(config)) throw new ApiException(ErrorCode.CONFLICT, "配置已保存，请重启管理服务", null);
        try {
            readiness.requireReady();
        } catch (ApiException exception) {
            if (exception.errorCode() == ErrorCode.DATABASE_NOT_INITIALIZED) return;
            throw exception;
        }
        throw new ApiException(ErrorCode.CONFLICT, "数据库已初始化，请从管理端维护配置", null);
    }

    /** 比较服务端预设凭据和用户输入，避免逐字符提前返回。 */
    private static boolean matches(String expected, String actual) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }

    /** 将连接失败转换为可安全展示的提示，省略底层地址和凭据相关细节。 */
    private static void testDatabase(SetupDatabase database) {
        try {
            database.test();
        } catch (SQLException | IOException exception) {
            throw invalid("无法连接业务数据库，请检查地址、账号或文件权限");
        }
    }

    /** 启用缓存时用独立连接发送 PING，验证网络及认证配置。 */
    private static void testRedis(SetupRequest.Redis redis) {
        var uri = RedisURI.Builder.redis(host(redis.host()), port(redis.port(), 6379))
                .withDatabase(redisDatabase(redis))
                .withTimeout(Duration.ofSeconds(3));
        if (redis.username() != null && !redis.username().isBlank()) {
            if (redis.password() == null || redis.password().isEmpty()) throw invalid("请填写 Redis 密码");
            uri.withAuthentication(redis.username(), redis.password());
        } else if (redis.password() != null && !redis.password().isEmpty()) {
            uri.withPassword(redis.password());
        }
        RedisClient client = RedisClient.create(uri.build());
        try (var connection = client.connect()) {
            if (!"PONG".equals(connection.sync().ping())) throw invalid("Redis 未返回有效的 PING 响应");
        } catch (RedisException exception) {
            throw invalid("无法连接 Redis，请检查地址、账号或密码");
        } finally {
            client.shutdown();
        }
    }

    /** 保存缓存开关及连接参数，关闭时不验证 Redis。 */
    private static void writeRedis(SetupRequest.Redis redis, Properties properties) {
        properties.setProperty("spring.cache.type", redis.enabled() ? "redis" : "none");
        if (!redis.enabled()) return;
        testRedis(redis);
        properties.setProperty("spring.data.redis.host", host(redis.host()));
        properties.setProperty("spring.data.redis.port", String.valueOf(port(redis.port(), 6379)));
        properties.setProperty("spring.data.redis.database", String.valueOf(redisDatabase(redis)));
        if (redis.username() != null && !redis.username().isBlank()) {
            properties.setProperty("spring.data.redis.username", redis.username());
        }
        if (redis.password() != null && !redis.password().isEmpty()) {
            properties.setProperty("spring.data.redis.password", redis.password());
        }
    }

    /** 校验主机名或 IPv4 地址，防止输入被解析为其他连接参数。 */
    static String host(String value) {
        if (value == null || !value.matches("[a-zA-Z0-9.-]{1,253}")) throw invalid("请填写有效的主机名或 IPv4 地址");
        return value;
    }

    /** 使用默认端口或校验显式输入的端口。 */
    static int port(Integer value, int defaultPort) {
        if (value == null) return defaultPort;
        if (value < 1 || value > 65535) throw invalid("端口须在 1 至 65535 之间");
        return value;
    }

    /** 使用默认 Redis 库 0，并拒绝负数库编号。 */
    private static int redisDatabase(SetupRequest.Redis redis) {
        int index = redis.database() == null ? 0 : redis.database();
        if (index < 0) throw invalid("Redis 库编号不能为负数");
        return index;
    }

    /** 尝试清理未发布的临时配置文件，避免异常路径遗留连接凭据。 */
    private static void removeStagedFile(Path staged) {
        if (staged == null) return;
        try {
            Files.deleteIfExists(staged);
        } catch (IOException exception) {
            log.warn("首装临时配置文件清理失败");
        }
    }

    /** 创建约定的首装参数错误响应。 */
    private static ApiException invalid(String message) {
        return new ApiException(ErrorCode.BAD_REQUEST, message, null);
    }
}
