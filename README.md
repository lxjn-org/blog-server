# 博客管理服务

本项目使用 Java 22、Maven、Spring Boot 4.1.3、Spring MVC 和 MyBatis-Plus 3.5.18。所有 HTTP 路径位于 /api/admin 下。

## 本地启动

在 blog-server 目录执行以下命令。Maven 使用的 JDK 应为 22。

```powershell
mvn.cmd -v
mvn.cmd package
java -jar target/blog-server-0.1.0-SNAPSHOT.jar
```

默认监听 8080，可通过 BLOG_SERVER_PORT 修改。数据库尚未配置时，后端仍可启动。/api/admin/health 和管理业务 API 在数据库未初始化时返回 HTTP 503、业务码 50301，管理端会进入 /admin/setup/database。已配置数据库的连接故障返回 HTTP 503、业务码 50000。/api/admin/actuator/health 采用 Spring Boot 的健康响应格式。

## HTTP 公共层

- 业务 Controller 使用 ApiResponse 返回 code、message 和 data。业务异常使用 ApiException，错误码集中维护在 ErrorCode 中。统一处理器转换参数、路由与数据库异常，并保留对应的 HTTP 状态。
- Servlet 过滤器生成 X-Request-Id，客户端可用它关联服务日志。MVC 拦截器记录路由模板、状态码和处理耗时。请求 ID 由 Hutool Core 生成。
- 同域 NGINX 部署及 Vite 代理可以直接使用。跨域直连时，将 BLOG_CORS_ALLOWED_ORIGINS 设置为逗号分隔的完整来源，如 http://localhost:5173。配置具体的协议、主机和端口，浏览器即可发送凭据并读取 X-Request-Id。

## 数据库

默认使用 sqlite 配置。通过 BLOG_JDBC_URL 指定现有数据库文件，如 jdbc:sqlite:file:D:/project/blog/blog-web/data/blog.db?mode=rw&busy_timeout=5000&foreign_keys=on。默认连接池仅使用一条 SQLite 连接。应用启动时不会创建业务库或执行建表、种子和迁移脚本。

手动连接 MySQL 时设置 SPRING_PROFILES_ACTIVE=mysql、BLOG_JDBC_URL、BLOG_DB_USER 和 BLOG_DB_PASSWORD。首装页保存 MySQL 时会自动写入 JDBC 连接和连接池配置。以环境变量或部署机密注入手动配置的连接凭据。

初始化或升级请按总仓库 docs/数据库表结构.md 执行 database/ 下的对应 SQL，成功后登记 component_version 中模块 0 的数据库版本。

## 首次安装

1. 在外部 Spring Boot 配置中设置 blog.setup.username 和 blog.setup.password，也可注入 BLOG_SETUP_USERNAME 和 BLOG_SETUP_PASSWORD。账号默认为 admin，密码由部署者设置，至少 12 个字符。管理页测试和保存时均须填写这组凭据。
2. 进入 /admin/setup/database。选择 SQLite 文件或已创建的 MySQL 库，并测试数据库连接。选中「使用现有数据库」会核对版本并保留现有表和账号。新库须为空，服务会运行对应建表脚本、登记版本，并保存初始管理员的 PBKDF2 密码哈希。
3. Redis 默认关闭。启用缓存时单独填写连接参数并测试。保存时服务会复查需要使用的连接。
4. 保存结果写入 BLOG_SETUP_CONFIG 指定的外部文件，默认是工作目录的 data/setup.properties。将此位置放在服务可写且仅服务账号可读的持久目录。Docker 部署需挂载该目录。应用实例由部署环境重启，重启后在首装页点击「检查数据库」进入登录页。

首装接口均位于 /api/admin/setup 下。POST /setup/database/test 和 POST /setup/redis/test 分别返回 connected，POST /setup 返回 restartRequired。接口在已有配置或业务库就绪时停止接收首装操作。

## Redis 缓存

默认关闭缓存。启用时设置 BLOG_CACHE_TYPE=redis，并通过 BLOG_REDIS_HOST、BLOG_REDIS_PORT 和 Spring Boot 的 SPRING_DATA_REDIS_PASSWORD 等环境变量配置连接。默认缓存有效期为 5 分钟，业务模块可按数据特点另行配置。Redis 访问异常由缓存层降级。健康检查以业务数据库为准。

## 当前接口范围

项目骨架提供健康检查和两个业务库的连接配置。管理员登录与应用内鉴权、文章发布、站点设置等业务模块将在后续按总仓库的接口契约接入。Redis 缓存配置已就绪，业务缓存项随业务模块接入。
