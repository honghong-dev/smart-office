# Smart-Office 开发踩坑日志

文档版本：v1.8
最后更新：2026-10-17
对应代码版本：Smart-Office v1.8

本文档记录了 Smart-Office 项目从零开发到 Docker 容器化部署、再到工业物联网模块接入的全过程中，遇到的所有关键问题、排查思路和解决方案。按时间顺序排列，每一个坑都是真实的实战经验。

---

## 第一阶段：基础环境与框架（1-20）

### 问题 1：项目启动报错，无法连接数据库
**报错信息**：`Failed to configure a DataSource: 'url' attribute is not specified...`
**原因**：`application.yml` 中缺少 `spring.datasource.url` 配置，或者层级写错。
**解决方案**：在 `application.yml` 中补全 url、username、password，并确保缩进正确。
**经验总结**：YAML 文件对缩进极其严格，写完配置后必须检查层级。

### 问题 2：JWT 鉴权失败，返回 401
**报错现象**：请求 `/api/user/list` 时返回 401，控制台打印 `Token is invalid` 或 `Token is null`。
**原因分析**：
1. 浏览器直接访问，默认不带 Header，Token 为 null。
2. 使用了 Bearer 前缀，但后端拦截器未剥离，导致解析失败。
3. 项目重启后，JWT 密钥重新随机生成，旧 Token 失效。
   **解决方案**：
1. 改用 Postman/Apifox，手动在 Headers 中添加 Authorization 字段。
2. 在 JwtInterceptor 中增加 `if (token.startsWith("Bearer "))` 判断，并执行 `token.substring(7)` 剥离前缀。
3. 将密钥提取到 `application.yml` 的 `jwt.secret` 中，并在 JwtUtils 中用 `@Value` 注入。
   **经验总结**：JWT 密钥绝对不能硬编码在代码里，必须放到配置文件中。

### 问题 3：分页查询报错，找不到符号 Page
**报错信息**：`Cannot resolve symbol 'Page'`
**原因**：导入了 Spring Data 的 Page，而不是 MyBatis-Plus 的 Page。
**解决方案**：选择导入 `com.baomidou.mybatisplus.extension.plugins.pagination.Page`。
**经验总结**：使用 MyBatis-Plus 分页时，必须配置 MybatisPlusConfig 中的 `PaginationInnerInterceptor`，否则分页会失效。

### 问题 4：Redis 缓存写入报错，TimeUnit 无法解析
**报错信息**：`Cannot resolve symbol 'TimeUnit'`
**原因**：导入了错误的 TimeUnit（来自 jsqlparser），而非 Java 原生的 `java.util.concurrent.TimeUnit`。
**解决方案**：选择导入 `java.util.concurrent.TimeUnit`。

### 问题 5：Maven 无法下载依赖，报"不知道这样的主机"
**报错信息**：`Could not transfer artifact ... from/to central: 不知道这样的主机。`
**原因**：Maven 默认仓库在国外，网络不稳定或被墙。
**解决方案**：在 `settings.xml` 中配置阿里云镜像，并在 IDEA 中勾选 Override 指向该文件。

### 问题 6：application.yml 中 jwt.secret 无法解析
**报错信息**：`Could not resolve placeholder 'jwt.secret'`
**原因**：jwt 配置写在了 spring 内部，导致 `@Value` 找不到。
**解决方案**：把 jwt 配置移到与 spring 同级的位置。

### 问题 7：Apifox/Postman 请求 Header 填错位置
**报错现象**：Token 已复制，但依然返回 401。
**原因**：把 Token 填到了 Query 参数里，而不是 Headers 里。
**解决方案**：在 Headers 标签页中，填写 Key 为 Authorization，Value 为 Bearer + Token。

### 问题 8：Postman 报 Header name must be a valid HTTP token
**原因**：Header 列表里的参数名（Key）和参数值（Value）填反了。
**解决方案**：确保左侧第一列只填参数名，右侧第二列只填参数值。

### 问题 9：SQL 唯一索引冲突
**报错信息**：`Duplicate entry 'zhangsan' for key 'sys_user.username'`
**原因**：注册时，用户名重复，触发了数据库的唯一索引约束。
**解决方案**：这是正常的保护机制。如果需要重新注册，先删除旧数据，或者换一个用户名。

### 问题 10：RabbitMQ 安装失败，浏览器无法访问 15672 端口
**报错现象**：访问 `http://localhost:15672` 显示"拒绝连接"。
**原因**：RabbitMQ 默认只启动了消息通信端口 5672，管理后台插件（15672）默认是关闭的。
**解决方案**：执行 `rabbitmq-plugins enable rabbitmq_management`，然后重启服务。

### 问题 11：RabbitMQ 消息堆积，消费者未生效
**报错现象**：管理后台显示 `Ready: 1, Consumers: 0`。
**原因**：忘记新建消费者类，或者该类缺少 `@Component` 注解。
**解决方案**：新建消费者类，加上 `@Component` 和 `@RabbitListener` 注解。

### 问题 12：Docker 容器内无法连接宿主机 MySQL
**报错信息**：`=== 全局异常捕获: 系统繁忙，请检查数据库 ===`
**原因分析**：
1. MySQL 8.0 默认使用 `caching_sha2_password` 加密方式，与容器内旧版驱动不兼容。
2. 容器内 `localhost` 指向容器自身。
3. Windows 防火墙可能拦截。
   **解决方案**：修改 MySQL 用户加密方式为 `mysql_native_password`；确保 `application.yml` 使用真实 IP 或 `host.docker.internal`。

### 问题 13：RabbitMQ 用户 ACCESS_REFUSED / NOT_ALLOWED
**报错信息**：`reply-code=530, reply-text=NOT_ALLOWED - access to vhost '/' refused`
**原因**：新创建的用户默认没有任何 vhost 的访问权限。
**解决方案**：在后台 Admin → Users 中点击 Set permission，填入 `.*`。

### 问题 14：Docker 构建镜像时报 failed to read dockerfile
**原因**：执行 `docker build` 时，当前命令行目录不在项目根目录下。
**解决方案**：先 `cd` 进入项目目录，再执行。命令最后的 `.` 代表当前目录。

### 问题 15：Maven 打包时测试失败，导致构建中断
**原因**：`@SpringBootTest` 启动整个上下文，连不上中间件导致失败。
**解决方案**：生产打包阶段执行 `mvn package -DskipTests`。

### 问题 16：Docker 端口冲突，8080 被占用
**原因**：IDEA 里正在跑的 SpringBoot 项目占用了 8080。
**解决方案**：用 `docker run -d -p 8081:8080 ...` 让容器对外暴露 8081。

### 问题 17：JWT 校验必须带 Bearer 前缀
**原因**：拦截器改成了"严格模式"。
**解决方案**：在 Header 里填 `Authorization: Bearer <Token>`。遵循 RFC 6750 标准。

### 问题 18：Redis 连接失败（Connection refused）
**原因**：Redis 的 bind 配置只监听 `127.0.0.1`，或者 `protected-mode` 开启。
**解决方案**：修改配置 `bind 0.0.0.0`，`protected-mode no`，设置密码并在 application.yml 配置。

### 问题 19：权限查询返回空数组
**原因**：数据库中 `sys_role_permission` 或 `sys_user_role` 表没有关联数据。
**解决方案**：手动插入关联数据。

### 问题 20：MQTTX 连接 EMQX 报 socket hang up
**原因**：MQTTX 的协议前缀选错。1883 端口走的是原生 MQTT 协议，如果选了 `ws://`，就连不上。
**解决方案**：协议前缀选 `mqtt://`，Port 填 1884。

---

## 第二阶段：物联网与高并发（21-40）

### 问题 21：Java 后端无法接收 MQTT 消息
**原因**：Broker 地址写错，或订阅 Topic 与发布 Topic 不匹配。
**解决方案**：确保配置一致。通配符 `+` 能匹配单层级，如 `factory/device/+/telemetry`。

### 问题 22：MQTT 数据入库性能瓶颈
**原因**：订阅回调单线程、串行处理，每条单独执行 INSERT。
**解决方案**：回调只入队 + 线程池批量插入 + 专用时序数据库（TDengine）。

### 问题 23：MQTT 项目刚启动时收不到脚本数据
**原因**：MQTT 订阅是异步的，落在"订阅窗口期"内。
**解决方案**：去掉 `Thread.sleep`，改用 `connectComplete()` 回调。

### 问题 24：本地 Paho jar 版本不对，setCleanStart 找不到
**解决方案**：删掉本地旧版本重新下载，或使用旧 API `setCleanSession(false)`。

### 问题 25：report_time 字段全是 NULL
**原因**：加了 `@TableField(fill = FieldFill.INSERT)` 但没实现 `MetaObjectHandler`。
**解决方案**：实现该接口并重写 `insertFill` 方法。

### 问题 26：MyMetaObjectHandler 重复定义，Spring bean 冲突
**原因**：两个包下有两个同名类。
**解决方案**：删掉多余的，保持项目内唯一。

### 问题 27：MQTT 脚本断开太早，最后一批消息没发出去
**原因**：QoS 1 的 publish 是异步的，脚本跑完立刻 disconnect。
**解决方案**：加 `Thread.sleep` 或用 `deliveryComplete` 回调计数器等待。

### 问题 28：批量插入"尾巴滚动"，压 N 条少 N-1 条
**原因**：最后一批不满 BATCH_SIZE，卡在 batch 里。
**解决方案**：加兜底，队列空了等 200ms 就 flush；把阻塞 `take()` 改成 `poll(100ms)`。

### 问题 29：take() 阻塞导致尾巴永远不 flush
**原因**：队列空了，线程卡在 `take()`，无法执行 flush 逻辑。
**解决方案**：改用带超时的 `poll()`。

### 问题 30：EMQX "无订阅者"丢弃消息
**原因**：订阅端掉线瞬间，EMQX 认为没有订阅者，直接丢消息。
**解决方案**：保证在线率，调大 `max_mqueue_len` 和 `max_inflight`。

### 问题 31：EMQX 离线队列积压，重连后一次性补发近 1 万条
**原因**：多次掉线导致离线队列积压过多。
**解决方案**：压测前清空 EMQX 会话，应用层做好幂等兜底。

### 问题 32：脚本 setCallback 在 connect 之后调用，deliveryComplete 不触发
**解决方案**：Paho 要求 `setCallback` 必须在 `connect` 之前调用。

### 问题 33：Thread.sleep 在脚本里可以，在生产代码里不行
**原因**：`sleep` 是时间赌注，网络抖动直接丢消息。
**解决方案**：生产代码必须用 `connectComplete()` 回调确认状态。

### 问题 34：RabbitMQ 队列属性不能热更新，改 TTL 要删队列
**原因**：队列属性在第一次创建时固定。
**解决方案**：删掉队列，重启项目让它重建。

### 问题 35：RabbitMQ 消息"至少一次"投递，消费端必须幂等
**解决方案**：消费端用 Redis SETNX 做去重。

### 问题 36：三级管理，同级不能改，不能越级
**解决方案**：业务逻辑做双向校验（校验目标当前角色 + 校验目标新角色）。

### 问题 37：@TableLogic 逻辑删除，注册时用户名冲突
**解决方案**：查询时加 `deleted = 0`，或将唯一索引改为 `(username, deleted)` 联合唯一。

### 问题 38：MySQL 大小写不敏感，Admin 和 admin 冲突
**解决方案**：注册时统一转小写，或使用 `utf8mb4_0900_as_cs` 排序规则。

### 问题 39：权限缓存刷新，Pub/Sub 删缓存 + 查库重建
**解决方案**：使用 Redis Pub/Sub 发送刷新指令，各实例收到后清理缓存。

### 问题 40：Docker Compose 端口冲突
**解决方案**：修改宿主机端口映射（如 `3307:3306`），避开本地服务。

---

## 第三阶段：容器编排与多模块（41-68）

### 问题 41：application-docker.yml 不生效
**原因**：配置文件优先级问题。
**解决方案**：用环境变量覆盖（`- MYSQL_HOST=mysql`），优先级最高。

### 问题 42：Redisson 读旧 IP
**原因**：Redisson 读 `spring.redis` 而不是 `spring.data.redis`。
**解决方案**：两个都配。

### 问题 43：JDK 17 Cgroup 报错
**原因**：`openjdk:17-jdk-slim` 镜像在 Windows Docker 下读不到 Cgroup。
**解决方案**：换 `eclipse-temurin` 镜像，或加 `-XX:-UseContainerSupport`。

### 问题 44：MySQL ASCII '\0' 报错
**原因**：客户端编码 latin1 读取 UTF-8 导致的乱码。
**解决方案**：导出或执行时加 `--default-character-set=utf8mb4`。

### 问题 45：MySQL 初始化不执行 SQL
**原因**：数据卷已存在，`docker-entrypoint-initdb.d` 中的脚本不会重跑。
**解决方案**：`docker-compose down -v` 删数据卷后重启。

### 问题 46：YAML 文件中文乱码
**解决方案**：IDEA 右下角将编码由 GBK 改为 UTF-8，并选择 Convert。

### 问题 47：sys_audit_log 表不存在
**原因**：只在 DBeaver 里建了表，没进初始化 SQL。
**解决方案**：将建表 SQL 加入 `docs/sql/smart_office.sql`，或手动去容器里建。

### 问题 48：数据权限，缓存串数据
**原因**：`cacheKey` 未带用户标识。
**解决方案**：`cacheKey` 加上 `currentUser.getId()`。

### 问题 49：Nacos 启动报 config_info_aggr 表不存在
**解决方案**：必须用官方完整版 `mysql-schema.sql` 建表。

### 问题 50：Nacos 发布配置报 Field 'nid' doesn't have a default value
**解决方案**：手动加上默认值 `ALTER TABLE his_config_info MODIFY COLUMN nid BIGINT NOT NULL DEFAULT 0;`。

### 问题 51：Nacos 删除配置报 Duplicate entry '7' for key 'his_config_info.PRIMARY'
**解决方案**：用官方完整版重建表。

### 问题 52：PowerShell 执行 SQL 报 You have an error in your SQL syntax
**原因**：PowerShell 默认 GBK 读文件。
**解决方案**：`Get-Content -Encoding UTF8` + `--default-character-set=utf8mb4`。

### 问题 53：Nacos 启动报 Unknown database 'nacos_config'
**解决方案**：手动用 PowerShell 导入 `nacos.sql`。

### 问题 54：SpringBoot 接 Nacos 后配置拉不到，env.getProperty 全 null
**原因**：缺 `spring-cloud-starter-bootstrap` 依赖。
**解决方案**：pom.xml 引入该依赖。

### 问题 55：本地 IDEA 和 Docker 的 SpringBoot 用同一 clientId，MQTT 连接互相挤掉
**解决方案**：本地开发 clientId 加 `-local` 后缀，并停掉 Docker 里的应用实例。

### 问题 56：Nacos 动态刷新后，旧 MQTT 连接不断开
**解决方案**：配置 `@PreDestroy` 在 Bean 销毁前主动断开 MQTT 连接。

### 问题 57：Docker Compose 引用未声明的网络，报 undefined network
**解决方案**：在文件底部加顶层 `networks` 块声明，或删掉服务单独的 networks 配置走默认网络。

### 问题 58：Docker 引擎没启动，报 npipe 连接失败
**解决方案**：确认 Docker Desktop 在后台运行。

### 问题 59：TDengine 原生连接报 no taos in java.library.path
**解决方案**：改用 REST 连接 `jdbc:TAOS-RS://`。

### 问题 60：改了配置没生效，TDengine URL 实际值还是旧的
**原因**：Nacos 优先级高于本地。
**解决方案**：打印运行时实际值，修改 Nacos 上的配置。

### 问题 61：TDengine 双写少数据，因 reportTime 为 null
**原因**：自动填充只在 MyBatis 插入时生效，内存对象没回填。
**解决方案**：在解析逻辑里显式 `setReportTime`。

### 问题 62：TDengine 多子表合并 INSERT 语法错误
**原因**：每个子表都带了 `INSERT INTO`。
**解决方案**：只有第一个子表带 `INSERT INTO`，后面的直接写子表名。

### 问题 63：死信表 message_id 重复，主键冲突
**原因**：用业务字段拼 message_id 导致重复。
**解决方案**：改用 `topic + nanoTime` 保证唯一；死信记录必须 try-catch，不能抛异常。

### 问题 64：@Slf4j 的 log 和字段 log 冲突
**解决方案**：类里的自定义字段避开 `log` 这个名字。

### 问题 65：本地 IDEA 和 Docker 同时跑 SpringBoot，MQTT 反复断连
**解决方案**：永远只跑一个实例。

### 问题 66：Nacos 未就绪，SpringBoot 启动报 BeanCreationException
**解决方案**：加 healthcheck 依赖等待，或等 Nacos 日志就绪后再启动。

### 问题 67：容器全停后，IDEA 里应用连不上一切
**解决方案**：区分 `docker compose stop`（停单个）和 `down`（停全部）。

### 问题 68：EMQX 5.x Dashboard 密码命令变更
**解决方案**：使用新命令 `emqx ctl admins passwd`。

---

## 第四阶段：多模块与高可用（69-84）

### 问题 69：多模块 Maven 重构后，simulation 模块 BUILD FAILURE
**解决方案**：给模块加启动类，或从父 pom 注释掉该模块。

### 问题 70：多模块重构后，Jackson 依赖缺失
**解决方案**：在 simulation 的 pom 中单独引入 `jackson-databind`。

### 问题 71：Maven 打包时缺 project.build.sourceEncoding 导致文件乱码
**解决方案**：父 pom 显式声明 `UTF-8`。

### 问题 72：TDengine 超表不存在，双写 100% 失败
**解决方案**：`TdengineWriteService.init()` 中加 `CREATE STABLE IF NOT EXISTS`。

### 问题 73：TDengine 多子表合并 INSERT，首批大批量建表失败
**解决方案**：拆两步，先显式建子表加缓存，再纯 INSERT 数据。

### 问题 74：MQTT cleanSession=true 导致 app 掉线期间数据全丢
**解决方案**：必须设为 `cleanSession=false` 并固定 clientId。

### 问题 75：MQTT 回调里做 IO 阻塞，ACK 推不出去
**解决方案**：回调里只入队，日志移到消费端，调大 `maxInflight`。

### 问题 76：EMQX 离线队列默认 1000 条，长时间离线丢数据
**解决方案**：配置 `EMQX_MQTT__MAX_MQUEUE_LEN=100000`。

### 问题 77：Docker 迁移后端口偏移，客户端连不上
**解决方案**：核对宿主机映射端口（如 3307、1884）。

### 问题 78：MQTT 持久会话导致路由表错乱（核心坑）
**原因**：固定 clientId 频繁重启，EMQX 保留旧会话导致消息投给死连接。
**解决方案**：踢掉僵尸客户端，或调整 cleanSession 策略。

### 问题 79：MQTT 扩字段后 NULL 值
**解决方案**：实体、解析、Mapper XML、数据库四处同步修改。

### 问题 80：AI Agent 异步处理抛 ConcurrentModificationException
**解决方案**：`@Async` 方法入口做防御性拷贝，遍历拷贝后的 `snapshot`。

### 问题 81：MySQL JSON 字段写入非法 JSON
**解决方案**：用 Jackson 序列化对象，绝不手拼 JSON 字符串。

### 问题 82：Spring AMQP 默认转换器不支持 POJO
**解决方案**：发 JSON 字符串（用 ObjectMapper 序列化）。

### 问题 83：告警风暴（同一设备重复告警）
**解决方案**：引入 Redis SETNX + TTL 5 分钟做冷却去重。

### 问题 84：规则引擎覆盖不全（数据追溯发现）
**原因**：某些故障（如轴承磨损）演化慢，瞬态故障时间短，未达阈值。
**解决方案**：补早预警规则（VIB_GT_5）、趋势规则、瞬时故障捕获。

---

## 第五阶段：真 LLM 接入与工程安全（85-92）

### 问题 85：IDEA 环境变量填错（两个变量粘一行）
**原因**：`AI_LLM_PROVIDER` 和 `DEEPSEEK_API_KEY` 填在了同一行。
**解决方案**：分两行独立填写。

### 问题 86：PowerShell 中文乱码（查 MySQL 表）
**解决方案**：执行 `chcp 65001` 并加 `--default-character-set=utf8mb4` 参数。

### 问题 87：git stash pop 冲突（add/add）
**解决方案**：手动解决标记后 `git add`，或直接抛弃 stash 重写。

### 问题 88：PowerShell 里 ${...} 被解析成变量
**解决方案**：commit message 使用单引号包裹，避免变量解析。

### 问题 89：githooks/pre-commit 自指拦截
**解决方案**：正则改为精确匹配 20 位以上字符串，并排除 `githooks/` 目录。

### 问题 90：Git hook 换行符 CRLF 导致执行失败
**解决方案**：用 PowerShell 强制写 LF 换行 + 无 BOM 的 UTF-8 文件。

### 问题 91：origin 的 push URL 被改成 GitHub
**解决方案**：`git remote set-url --push origin` 重置回 Gitee。

### 问题 92：Windows Notepad 保存文件自动加 .txt
**解决方案**：用 `Rename-Item` 改名，或关闭系统“隐藏扩展名”选项。