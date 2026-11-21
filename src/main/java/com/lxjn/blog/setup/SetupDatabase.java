package com.lxjn.blog.setup;

import com.lxjn.blog.web.ApiException;
import com.lxjn.blog.web.ErrorCode;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Base64;
import java.util.Properties;

record SetupDatabase(String type, Path path, String url, String username, String password, boolean reuse) {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int ITERATIONS = 600_000;

    /** 将页面字段转换为受约束的 JDBC 配置，不接收任意 JDBC 参数。 */
    static SetupDatabase from(SetupRequest.Database request) {
        boolean reuse = Boolean.TRUE.equals(request.reuse());
        return switch (request.type()) {
            case "sqlite" -> {
                if (request.path() == null || request.path().isBlank()) throw invalid("请填写 SQLite 文件路径");
                Path path = Path.of(request.path()).toAbsolutePath().normalize();
                yield new SetupDatabase("sqlite", path, sqliteUrl(path, false), "", "", reuse);
            }
            case "mysql" -> {
                String host = SetupService.host(request.host());
                int port = SetupService.port(request.port(), 3306);
                if (request.name() == null || !request.name().matches("[a-zA-Z0-9_-]{1,64}")) {
                    throw invalid("MySQL 数据库名须由字母、数字、下划线或短横线组成");
                }
                if (request.username() == null || request.username().isBlank()) throw invalid("请填写 MySQL 用户名");
                String url = "jdbc:mysql://" + host + ":" + port + "/" + request.name()
                        + "?connectTimeout=3000&socketTimeout=5000";
                yield new SetupDatabase("mysql", null, url, request.username(), emptyIfNull(request.password()), reuse);
            }
            default -> throw invalid("请选择 SQLite 或 MySQL");
        };
    }

    /** 测试数据库连接。新 SQLite 文件使用同一目录的临时库验证创建权限。 */
    void test() throws IOException, SQLException {
        if (path == null || Files.exists(path) || reuse) {
            try (Connection connection = connect(url)) {
                if (!connection.isValid(3)) throw new SQLException("连接校验失败");
            }
            return;
        }
        Path directory = path.getParent();
        while (directory != null && !Files.exists(directory)) directory = directory.getParent();
        if (directory == null || !Files.isDirectory(directory)) throw invalid("SQLite 路径的上级目录无效");
        Path probe = Files.createTempFile(directory, ".sqlite-check-", ".db");
        try (Connection connection = connect(sqliteUrl(probe, false))) {
            if (!connection.isValid(3)) throw new SQLException("连接校验失败");
        } finally {
            Files.deleteIfExists(probe);
        }
    }

    /** 复用时只核对已登记的版本，否则对空库执行建表并写入初始管理员。 */
    void prepare(SetupRequest.Credentials admin) throws SQLException, IOException, GeneralSecurityException {
        int version = expectedVersion();
        if (path != null && !reuse) Files.createDirectories(path.getParent());
        try (Connection connection = connect(path == null ? url : sqliteUrl(path, !reuse))) {
            if (reuse) {
                requireExistingVersion(connection, version);
                return;
            }
            try (var tables = connection.getMetaData().getTables(connection.getCatalog(), null, "%", new String[]{"TABLE", "VIEW"})) {
                if (tables.next()) throw invalid("目标库已有表或视图，请选择复用或指定空库");
            }
            String hash = passwordHash(admin.password());
            connection.setAutoCommit(false);
            try {
                ScriptUtils.executeSqlScript(connection, new ClassPathResource("database/schema." + type + ".sql"));
                String now = new DateTimeFormatterBuilder().appendInstant(3).toFormatter().format(Instant.now());
                try (var insert = connection.prepareStatement(
                        "INSERT INTO admin_user (username, password_hash, created_at, updated_at) VALUES (?, ?, ?, ?)")) {
                    insert.setString(1, admin.username());
                    insert.setString(2, hash);
                    insert.setString(3, now);
                    insert.setString(4, now);
                    insert.executeUpdate();
                }
                try (var update = connection.prepareStatement(
                        "UPDATE component_version SET current_version = ?, updated_at = ? WHERE module = 0")) {
                    update.setInt(1, version);
                    update.setString(2, now);
                    if (update.executeUpdate() != 1) throw new SQLException("数据库版本登记失败");
                }
                connection.commit();
            } catch (RuntimeException | SQLException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    /** 写入重启后使用的数据库配置，SQLite 启用单连接池。 */
    void write(Properties config) {
        config.setProperty("spring.datasource.url", url);
        config.setProperty("spring.datasource.username", username);
        config.setProperty("spring.datasource.password", password);
        config.setProperty("spring.datasource.hikari.maximum-pool-size", path == null ? "10" : "1");
    }

    /** 核对现有库版本，避免将空库或旧库标记为可用。 */
    private static void requireExistingVersion(Connection connection, int expected) throws SQLException {
        try (var query = connection.prepareStatement("SELECT current_version FROM component_version WHERE module = 0");
             var result = query.executeQuery()) {
            if (!result.next() || result.getInt(1) < expected) {
                throw invalid("现有数据库版本低于当前要求，请先升级");
            }
        }
    }

    /** 读取构建产物中配套的数据库版本。 */
    private static int expectedVersion() throws IOException {
        try (var input = new ClassPathResource("database/version.json").getInputStream()) {
            return new JsonMapper().readTree(input).path("version").asInt();
        }
    }

    /** 生成带独立盐值的初始密码哈希，供管理员认证模块复用。 */
    private static String passwordHash(String password) throws GeneralSecurityException {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        var spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, 256);
        try {
            byte[] hash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            var base64 = Base64.getEncoder();
            return "pbkdf2-sha256$" + ITERATIONS + "$" + base64.encodeToString(salt) + "$" + base64.encodeToString(hash);
        } finally {
            spec.clearPassword();
        }
    }

    /** 打开指定数据库的短生命周期连接。 */
    private Connection connect(String jdbcUrl) throws SQLException {
        return DriverManager.getConnection(jdbcUrl, username, password);
    }

    /** 使用文件 URI 保留路径中的空格等字符，并明确 SQLite 打开模式。 */
    private static String sqliteUrl(Path path, boolean create) {
        return "jdbc:sqlite:" + path.toUri().toASCIIString() + "?mode=" + (create ? "rwc" : "rw")
                + "&busy_timeout=5000&foreign_keys=on";
    }

    /** 将未填写的可选凭据转换为空字符串。 */
    private static String emptyIfNull(String value) {
        return value == null ? "" : value;
    }

    /** 生成可安全展示的数据库参数错误。 */
    private static ApiException invalid(String message) {
        return new ApiException(ErrorCode.BAD_REQUEST, message, null);
    }
}
