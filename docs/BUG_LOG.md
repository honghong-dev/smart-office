# Smart-Office 开发踩坑日志

本文档记录了 Smart-Office 项目从零开发到 Docker 容器化部署、再到工业物联网模块接入的全过程中，遇到的所有关键问题、排查思路和解决方案。

按时间顺序排列，每一个坑都是真实的实战经验。

---

问题 1：项目启动报错，无法连接数据库

报错信息：Failed to configure a DataSource: 'url' attribute is not specified...

原因：application.yml 中缺少 spring.datasource.url 配置，或者层级写错。

解决方案：在 application.yml 中补全 url、username、password，并确保缩进正确。

经验总结：YAML 文件对缩进极其严格，写完配置后必须检查层级。

---

问题 2：JWT 鉴权失败，返回 401

报错现象：请求 /api/user/list 时返回 401，控制台打印 Token is invalid 或 Token is null。

原因分析：

- 浏览器直接访问，默认不带 Header，Token 为 null。
- 使用了 Bearer 前缀，但后端拦截器未剥离，导致解析失败。
- 项目重启后，JWT 密钥重新随机生成，旧 Token 失效。

解决方案：

- 改用 Postman/Apifox，手动在 Headers 中添加 Authorization 字段。
- 在 JwtInterceptor 中增加 if (token.startsWith("Bearer ")) 判断，并执行 token.substring(7) 剥离前缀。
- 将密钥提取到 application.yml 的 jwt.secret 中，并在 JwtUtils 中用 @Value 注入，保证密钥固定。

经验总结：JWT 密钥绝对不能硬编码在代码里，必须放到配置文件中。

---

问题 3：分页查询报错，找不到符号 Page

报错信息：Cannot resolve symbol 'Page'

原因：导入了 Spring Data 的 Page，而不是 MyBatis-Plus 的 Page。

解决方案：选择导入 com.baomidou.mybatisplus.extension.plugins.pagination.Page。

经验总结：使用 MyBatis-Plus 分页时，必须配置 MybatisPlusConfig 中的 PaginationInnerInterceptor，否则分页会失效。

---

问题 4：Redis 缓存写入报错，TimeUnit 无法解析

报错信息：Cannot resolve symbol 'TimeUnit'

原因：导入了错误的 TimeUnit（来自 jsqlparser），而非 Java 原生的 java.util.concurrent.TimeUnit。

解决方案：选择导入 java.util.concurrent.TimeUnit。

经验总结：在 IDEA 导包时，务必看清包的来源。

---

问题 5：Maven 无法下载依赖，报"不知道这样的主机"

报错信息：Could not transfer artifact ... from/to central: 不知道这样的主机。

原因：Maven 默认仓库在国外，网络不稳定或被墙。

解决方案：在 settings.xml 中配置阿里云镜像，并在 IDEA 中勾选 Override 指向该文件。

经验总结：国内开发环境，Maven 必须配置国内镜像。

---

问题 6：application.yml 中 jwt.secret 无法解析

报错信息：Could not resolve placeholder 'jwt.secret'

原因：jwt 配置写在了 spring 内部，导致 @Value 找不到。

解决方案：把 jwt 配置移到与 spring 同级的位置。

经验总结：YAML 中的配置层级非常重要，顶级配置不能缩进在 spring 下面。

---

问题 7：Apifox/Postman 请求 Header 填错位置

报错现象：Token 已复制，但依然返回 401。

原因：把 Token 填到了 Query 参数里，而不是 Headers 里。

解决方案：在 Headers 标签页中，填写 Key 为 Authorization，Value 为 Bearer + Token。

经验总结：HTTP 请求中，认证信息必须放在 Headers 中。

---

问题 8：Postman 报 Header name must be a valid HTTP token

报错现象：请求直接报错，连 401 都收不到。

原因：Header 列表里的参数名（Key）和参数值（Value）填反了。

解决方案：确保左侧第一列只填参数名（如 Authorization），右侧第二列只填参数值（如 Bearer xxx）。

经验总结：Postman/Apifox 的 Header 是键值对结构，不能自己造格式。

---

问题 9：SQL 唯一索引冲突

报错信息：Duplicate entry 'zhangsan' for key 'sys_user.username'

原因：注册时，用户名重复，触发了数据库的唯一索引约束。

解决方案：这是正常的保护机制。如果需要重新注册，先删除旧数据，或者换一个用户名。

经验总结：唯一索引是防止数据重复的最后一道防线。

---

问题 10：RabbitMQ 安装失败，浏览器无法访问 15672 端口

报错现象：访问 http://localhost:15672 显示"拒绝连接"。

原因：RabbitMQ 默认只启动了消息通信端口 5672，管理后台插件（15672）默认是关闭的。

解决方案：执行 rabbitmq-plugins enable rabbitmq_management，然后重启 RabbitMQ 服务。

经验总结：RabbitMQ 的插件机制需要手动开启。

---

问题 11：RabbitMQ 消息堆积，消费者未生效

报错现象：管理后台显示 Ready: 1，Consumers: 0。

原因：忘记新建消费者类，或者该类缺少 @Component 注解。

解决方案：新建消费者类，加上 @Component 和 @RabbitListener(queues = RabbitMQConfig.REGISTER_QUEUE) 注解。

经验总结：消息队列的"生产者"和"消费者"必须是成对出现的。

---

问题 12：Docker 容器内无法连接宿主机 MySQL

报错信息：=== 全局异常捕获: 系统繁忙，请检查数据库 ===

原因分析：

- MySQL 8.0 默认使用 caching_sha2_password 加密方式，与容器内旧版驱动不兼容。
- 容器内 localhost 指向容器自身，而非宿主机。
- Windows 防火墙可能拦截来自 Docker 网卡的请求。

解决方案：

- ALTER USER 'root'@'%' IDENTIFIED WITH mysql_native_password BY '123456';
- FLUSH PRIVILEGES;
- 确保 application.yml 使用宿主机真实 IP。

经验总结：容器化 MySQL 连接的核心难点在于"网络隔离 + 加密方式 + 用户权限"三者叠加。

---

问题 13：RabbitMQ 用户 ACCESS_REFUSED / NOT_ALLOWED

报错信息：reply-code=530, reply-text=NOT_ALLOWED - access to vhost '/' refused

原因：新创建的用户默认没有任何 vhost 的访问权限。

解决方案：在 RabbitMQ 管理后台 → Admin → Users → 点击用户 → 在 Permissions 区域填入 .* 并点击 Set permission。

经验总结：RabbitMQ 默认的 guest 用户只能从 localhost 登录。

---

问题 14：Docker 构建镜像时报 failed to read dockerfile

报错信息：failed to read dockerfile: open Dockerfile: no such file or directory

原因：执行 docker build 时，当前命令行目录不在项目根目录下。

解决方案：先 cd /d D:\IdeaProjects\smart-office 进入项目目录，再执行 docker build -t smart-office:1.0 .。

经验总结：docker build 命令最后的 . 代表"当前目录"。

---

问题 15：Maven 打包时测试失败，导致构建中断

报错信息：Tests run: 2, Failures: 0, Errors: 1, Skipped: 0，最终 BUILD FAILURE。

原因：@SpringBootTest 测试类会启动整个 SpringBoot 上下文，如果测试环境连不上数据库/Redis/RabbitMQ，测试就会失败。

解决方案：打包时跳过测试，执行 mvn package -DskipTests。

经验总结：生产打包阶段不应该被测试环境不稳定所阻塞。

---

问题 16：Docker 端口冲突，8080 被占用

报错信息：Web server failed to start. Port 8080 was already in use.

原因：IDEA 里正在跑的 SpringBoot 项目占用了 8080。

解决方案：用 docker run -d -p 8081:8080 ... 让容器对外暴露 8081。

经验总结：-p 8081:8080 中，冒号左边是宿主机端口，右边是容器端口。

---

问题 17：JWT 校验必须带 Bearer 前缀

报错信息：Token is missing 或 Token is invalid

原因：拦截器改成了"严格模式"，必须校验 Bearer 前缀。

解决方案：在 Header 里填 Authorization: Bearer <Token>。

经验总结：Bearer 是 HTTP 标准（RFC 6750），企业级项目必须严格遵循。

---

问题 18：Redis 连接失败（Connection refused）

报错信息：Unable to connect to Redis

原因：Redis 的 bind 配置只监听 127.0.0.1，或者 protected-mode 开启。

解决方案：

- 修改 redis.windows-service.conf：bind 0.0.0.0，protected-mode no。
- 设置密码：requirepass 123456。
- 在 application.yml 里加上 password: 123456。

经验总结：Redis 默认只允许本机访问。跨网络访问必须改 bind + 关闭保护模式 + 设置密码。

---

问题 19：权限查询返回空数组

报错现象：GET /api/user/permissions/1 返回 data: []

原因：数据库中 sys_role_permission 或 sys_user_role 表没有关联数据。

解决方案：手动插入关联数据：

```sql
INSERT INTO sys_role_permission (role_id, permission_id) VALUES (1, 1), (1, 2);
INSERT INTO sys_user_role (user_id, role_id) VALUES (123, 1);
```
经验总结：RBAC 模型的数据是"链式"

---

问题 20：MQTTX 连接 EMQX 报 socket hang up

报错信息：Error: socket hang up

原因：MQTTX 的协议前缀选错。1883 端口走的是原生 MQTT 协议，如果选了 ws://，就连不上。

解决方案：协议前缀选 mqtt://，Host 填宿主机 IP，Port 填 1884（容器化后映射端口）。

经验总结：MQTT 端口 1883 对应 mqtt:// 协议；WebSocket 端口 8083 对应 ws:// 协议。容器化后，宿主机映射端口是 1884。

---
问题 21：Java 后端无法接收 MQTT 消息

报错现象：MQTTX 发送成功，但 IDEA 控制台没有任何日志。

原因分析：

Java 后端的 MQTT Broker 地址写错（用了 localhost 而不是宿主机 IP）。

订阅的 Topic 和发布的 Topic 不匹配。

解决方案：确保 MqttConfig 里的 broker 地址与 EMQX 实际地址一致。

经验总结：MQTT 的 Topic 匹配规则很严格，factory/device/+/temperature 能匹配 factory/device/001/temperature。

---

问题 22：MQTT 数据入库性能瓶颈（已完成优化）

报错现象：100 条数据，发送端耗时 96 毫秒，但后端串行处理需要数秒。

原因：MqttSubscriber 是单线程、串行处理，每条数据都要单独执行一次 INSERT。

解决方案（已落地）：

MQTT 回调线程只入队。

线程池 + 批量插入（4 线程，1000 条/批）。

Redis 幂等。

死信记录。

待优化：换 TDengine。

经验总结：工业物联网的核心难点是"高并发写入"。必须异步化 + 批量插入 + 专用时序数据库。

---

问题 23：MQTT 项目刚启动时收不到脚本数据

报错现象：项目启动后立即运行脚本，脚本发送 100 条数据，但项目控制台没有收到。

原因：MQTT 订阅是异步的，脚本发送太快，落在"订阅窗口期"内，消息被 EMQX 丢弃。

解决方案（已落地）：

去掉 Thread.sleep(1000)，改用 connectComplete() 回调。

QoS 1 + 持久会话。

应用层幂等。

经验总结：这是分布式系统的"竞态条件"。必须用 QoS 1 + 持久会话 + 幂等校验三层保障。

---

问题 24：本地 Paho jar 版本不对，setCleanStart 找不到

报错信息：java: 找不到符号 符号: 方法 setCleanStart(boolean)

原因：本地 .m2 仓库里的 jar 是旧版本，只有 setCleanSession。

解决方案：删掉本地 .m2 里的 paho 旧版本，重新下载。临时方案：用 setCleanSession(false)。

经验总结：Maven 依赖版本和本地 jar 不一致，是常见坑。

---

问题 25：report_time 字段全是 NULL

报错现象：设备遥测数据入库后，report_time 字段全是 NULL。

原因：@TableField(fill = FieldFill.INSERT) 加了，但没实现 MetaObjectHandler。

解决方案：新建 MyMetaObjectHandler 类，实现 MetaObjectHandler，在 insertFill 里填充。

经验总结：MyBatis-Plus 的自动填充，必须配 MetaObjectHandler。

---

问题 26：MyMetaObjectHandler 重复定义，Spring bean 冲突

报错信息：ConflictingBeanDefinitionException

原因：两个包下，有两个同名 MyMetaObjectHandler 类。

解决方案：删掉其中一个，只留 config 包下的。

经验总结：同一个项目下，不要建两个同名的 public 类。

---

问题 27：MQTT 脚本断开太早，最后一批消息没发出去

报错现象：脚本发 50000 条，数据库只入库 49501 条，少了 499 条。

原因：QoS 1 的 publish 是异步的，脚本跑完立刻 disconnect，缓冲区里的消息还没发出去。

解决方案：脚本里加 Thread.sleep(10000)，或者用 deliveryComplete 回调计数器。

经验总结：QoS 1 的 publish 是异步的，disconnect 前必须等所有消息确认。

---

问题 28：批量插入"尾巴滚动"，压 N 条少 N-1 条

报错现象：压 10000 条，入库 9889 条，少了 111 条。下次压，又多了 236 条。

原因：批量攒批，最后一批不满 BATCH_SIZE，卡在 batch 里，等下次凑批。

解决方案（已落地）：

加兜底：队列空了等 200ms 就 flush。

把 take() 改成 poll(100ms)。

经验总结：批量插入的"尾巴"问题，是标准写法的固有缺陷。

---

问题 29：take() 阻塞导致尾巴永远不 flush

报错现象：压 1000 条，入库 900 多，等 1 分钟还是 900 多。

原因：take() 阻塞，队列空了，batch 里的尾巴没机会被检查、flush。

解决方案：把 take() 改成 poll(100ms, TimeUnit.MILLISECONDS)。

经验总结：阻塞 take() 是批量消费的经典陷阱。

---

问题 30：EMQX "无订阅者"丢弃消息

报错现象：EMQX Dashboard 显示"消息丢弃 4703 条"，丢弃原因 100% 是"无订阅者"。

原因：订阅端在压测过程中掉线了。EMQX 认为这个 topic 下没有订阅者，把消息直接丢弃。

解决方案：

保证订阅端稳定在线。

调大 EMQX 的 max_mqueue_len 和 max_inflight。

工业级方案：订阅端多实例 + 共享订阅。

经验总结：EMQX 的"无订阅者"丢弃，是订阅端掉线导致的。

---

问题 31：EMQX 离线队列积压，重连后一次性补发近 1 万条

报错现象：清库后，压 10000 条，数据库入库 19971 条，多了 9971 条。

原因：多次压测中，订阅端反复掉线，EMQX 离线队列积压了近 1 万条消息。

解决方案：

压测前，清空 EMQX 会话。

压测前，清空数据库、Redis 幂等 key、SpringBoot 内存队列。

接受补发，用幂等兜底。

经验总结：持久会话的"补发"是双刃剑。好处是不丢消息，坏处是重连时一次性推大量消息。

---

问题 32：脚本 setCallback 在 connect 之后调用，deliveryComplete 不触发

报错现象：脚本用 deliveryComplete 回调计数器等 PUBACK，但计数器一直是 0。

原因：Paho 要求 setCallback 在 connect 之前调用。

解决方案：把 client.setCallback(...) 挪到 client.connect(options) 之前。

经验总结：Paho 的回调设置顺序很重要。

---

问题 33：Thread.sleep 在脚本里可以，在生产代码里不行

报错现象：生产代码里用 Thread.sleep(1000) 等 EMQX 订阅就绪，压测时丢消息。

原因：Thread.sleep 是"时间赌注"，不是"状态确认"。

解决方案：

生产代码：用 connectComplete() 回调。

脚本：可以用 Thread.sleep(10000) 等异步发送完成。

经验总结：脚本层面的 sleep 可以接受，生产代码的 sleep 是补丁。

---

问题 34：RabbitMQ 队列属性不能热更新，改 TTL 要删队列

报错现象：改 RabbitMQConfig 的 TTL，从 30 分钟改成 10 秒，重启 SpringBoot 后，队列的 TTL 还是 30 分钟。

原因：RabbitMQ 的队列属性（TTL、死信交换机）只在队列"第一次创建"时生效。

解决方案：删掉队列，重启 SpringBoot，让它用新的 TTL 重建。

经验总结：RabbitMQ 的队列，是"声明式"创建，属性不能热更新。这和 Kafka 的 Topic 一样，属性创建时固定。

---

问题 35：RabbitMQ 消息"至少一次"投递，消费端必须幂等

报错现象：ApprovalTimeoutConsumer 收到同一条 approvalId=3 两次，发了两次提醒。

原因：RabbitMQ 的投递语义是"至少一次"（At Least Once），重复投递是正常的。

解决方案：消费端用 Redis SETNX 做幂等。key 是 approval:timeout:{approvalId}。

经验总结：QoS 1 和 RabbitMQ 的"至少一次"，都可能在消费端重复。所以，消费端必须幂等。

---

问题 36：三级管理，同级不能改，不能越级

报错现象：班长（SUB_ADMIN）能改另一个班长（SUB_ADMIN），能改厂长（ADMIN），甚至能造厂长。

原因：assignRole 只校验了"目标用户是 ADMIN"，没校验"目标用户是同级 SUB_ADMIN"，也没校验"目标角色是 ADMIN"。

解决方案：

班长不能改同级：if (isSubAdmin && targetRoles.contains("SUB_ADMIN")) 拒绝。

班长不能把别人改成 ADMIN：if (isSubAdmin && roleIds.contains(adminRole.getId())) 拒绝。

班长能把员工改成班长：不拦，这是"向下管理"。

不能造第二个 SUPER_ADMIN：if (roleIds.contains(superAdminRole.getId())) 拒绝。

经验总结：权限管理，要做"双向校验"：一是校验目标用户当前的角色，二是校验目标用户要改成的新角色。防止越权提升。

---

问题 37：@TableLogic 逻辑删除，注册时用户名冲突

报错现象：删了 admin 用户，但注册 admin 时，报"用户名已存在"。

原因：SysUser 的 deleted 字段加了 @TableLogic，删除是"逻辑删除"，记录还在。

解决方案：

注册时，查 username，加 deleted = 0 条件。

或者，物理删除，彻底删掉。

或者，username 唯一索引改成 username + deleted 的联合唯一索引。

经验总结：逻辑删除的记录，username 唯一索引还在，注册时会冲突。

---

问题 38：MySQL 大小写不敏感，Admin 和 admin 冲突

报错现象：注册 admin，报"用户名已存在"，但数据库里是 Admin。

原因：MySQL 的 utf8mb4_0900_ai_ci 排序规则，默认是"大小写不敏感"的。

解决方案：

注册时，把用户名统一转小写。

或者，用 utf8mb4_0900_as_cs（大小写敏感）。

经验总结：MySQL 默认大小写不敏感，Admin 和 admin 会冲突。工业级做法：注册时，用户名统一转小写。

---

问题 39：权限缓存刷新，Pub/Sub 删缓存 + 查库重建

报错现象：管理员改权限后，用户不重新登录，权限不生效。

原因：权限缓存在 Redis，30 分钟过期。改了数据库，Redis 缓存没更新。

解决方案：

改权限后，发消息到 permission:refresh 频道。

各 Java 实例订阅这个频道，收到消息后，删掉对应用户的权限缓存。

JwtInterceptor 里，缓存未命中时，查数据库，重建缓存。

经验总结：权限缓存刷新，用 Redis Pub/Sub。缓存未命中时，查库重建，用户无感。权限变更，秒级生效。

---

问题 40：Docker Compose 端口冲突

报错现象：

text
Error starting userland proxy: listen tcp4 0.0.0.0:3306: bind: address already in use
原因分析：本地 MySQL 服务（Windows 服务）占用了宿主机 3306，容器 MySQL 也想映射到 3306，端口冲突。

解决方案：改容器映射端口。

yaml
mysql:
  ports:
    - "3307:3306"   # 宿主机 3307 -> 容器 3306

redis:
  ports:
    - "6380:6379"

rabbitmq:
  ports:
    - "5673:5672"
    - "15673:15672"

emqx:
  ports:
    - "1884:1883"
    - "18084:18083"
    - "8084:8083"
经验总结：端口冲突，是"宿主机端口"冲突。容器内部，用标准端口（3306、6379）。宿主机和容器，用不同端口，避免冲突。生产环境，服务器上只有 Docker，没有本地服务，容器端口直接映射到标准端口，不冲突。

---

问题 41：application-docker.yml 不生效

报错现象：容器里，SpringBoot 连的还是旧 IP，不是服务名（mysql、redis）。

原因分析：

SPRING_PROFILES_ACTIVE=docker 生效了。

application-docker.yml 也打包进 JAR 了。

但 application-docker.yml 里的配置，没"覆盖"application.yml 的配置。

解决方案：用环境变量覆盖。在 docker-compose.yml 里，给 smart-office 服务加 environment：

yaml
smart-office:
  environment:
    - SPRING_PROFILES_ACTIVE=docker
    - MYSQL_HOST=mysql
    - REDIS_HOST=redis
    - RABBITMQ_HOST=rabbitmq
    - MQTT_HOST=emqx
    - SPRING_CLOUD_NACOS_CONFIG_SERVER_ADDR=nacos:8848
经验总结：环境变量的优先级，比 application.yml、application-docker.yml 都高。SpringBoot 的"宽松绑定"：MQTT_HOST → ${MQTT_HOST:localhost}。

---

问题 42：Redisson 读旧 IP

报错现象：

text
o.redisson.connection.ConnectionsHolder : 1 connections initialized for 10.224.4.209/10.224.4.209:6379
原因分析：Redisson 的配置，读的是 spring.redis（旧版），不是 spring.data.redis（新版）。

解决方案：在 application-docker.yml 里，两个都配：

yaml
spring:
  data:
    redis:
      host: redis
      port: 6379
      password: 123456

  redis:   # 兼容旧版 Redisson
    host: redis
    port: 6379
    password: 123456
经验总结：Redisson 的 spring-boot-starter，读 spring.data.redis。但某些版本，读 spring.redis。两个都配，避免踩坑。

---

问题 43：JDK 17 Cgroup 报错

报错现象：

text
java.lang.NullPointerException: Cannot invoke "jdk.internal.platform.CgroupInfo.getMountPoint()" because "anyController" is null
原因分析：openjdk:17-jdk-slim 镜像，在 Windows Docker Desktop 下，JDK 17 的"容器感知"读 Cgroup 信息，读到 null，报空指针。

解决方案：换基础镜像，或者加 JVM 参数。

方案一：换镜像

dockerfile
FROM eclipse-temurin:17-jre-alpine
方案二：加 JVM 参数

dockerfile
ENTRYPOINT ["java", "-XX:-UseContainerSupport", "-jar", "app.jar"]
经验总结：JDK 17 的"容器感知"，会读 Cgroup。在某些 Docker 环境（Windows Docker Desktop）下，Cgroup 信息读不到，报 NullPointerException。换 eclipse-temurin，或加 -XX:-UseContainerSupport。

---

问题 44：MySQL ASCII '\0' 报错

报错现象：

text
ERROR: ASCII '\0' appeared in the statement, but this is not allowed unless option --binary-mode is enabled
Query: '��-'
原因分析：

mysqldump 导出 SQL 时，用了系统编码（GBK）。

MySQL 容器里，mysql 客户端，默认用 latin1。

latin1 读 UTF-8 的中文，乱码，出现 \0。

解决方案：

方案一：导出时，加 --default-character-set=utf8mb4

bash
mysqldump --default-character-set=utf8mb4 --databases smart_office > smart_office.sql
方案二：SQL 文件开头，加 SET NAMES utf8mb4;

方案三：用 DBeaver 导出，勾"用十六进制转存二进制字段"

经验总结：MySQL 客户端，默认用 latin1。读 UTF-8 的中文，就乱码，甚至报 ASCII '\0'。导出时，加 --default-character-set=utf8mb4。

---

问题 45：MySQL 初始化不执行 SQL

报错现象：docker-compose up -d，MySQL 起来了，但 smart_office 库是空的，没有表。

原因分析：docker-entrypoint-initdb.d 里的 SQL，只在"数据目录为空"时执行。如果数据卷（mysql-data）已经存在，就不执行。

解决方案：删数据卷，重新初始化。

bash
docker-compose down -v
docker-compose up -d --build
-v：删数据卷。

经验总结：docker-entrypoint-initdb.d 里的 SQL，只在 MySQL 第一次启动时执行。要重新初始化，得 docker-compose down -v，删数据卷。

---

问题 46：YAML 文件中文乱码

报错现象：

yaml
redis: # 鍏煎鏃х増 Redisson
原因分析：YAML 文件，被"非 UTF-8 编码"读，中文乱码。

解决方案：

IDEA 里，看右下角，编码是不是 UTF-8。

如果是 GBK，点它，选 UTF-8。

弹窗选"Convert"（转换），不是"Reload"（重新加载）。

保存，重新打包。

经验总结：YAML 默认用 UTF-8。如果文件是 GBK，中文会乱码，甚至解析失败。我遇到过"application-docker.yml 不生效"的坑，检查编码，发现是 GBK。

---

问题 47：sys_audit_log 表不存在

报错现象：审计日志有 log.info，但数据库里没有记录。

原因分析：sys_audit_log 表，是在 DBeaver 里建的，没进 docs/sql/smart_office.sql。MySQL 容器启动时，只执行 smart_office.sql，不执行 DBeaver 里建的。

解决方案：进 MySQL 容器，手动建表：

sql
CREATE TABLE sys_audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    operator_id BIGINT NOT NULL,
    operator_name VARCHAR(50),
    operation VARCHAR(50) NOT NULL,
    target_id BIGINT,
    target_type VARCHAR(50),
    old_value VARCHAR(500),
    new_value VARCHAR(500),
    ip VARCHAR(50),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    KEY idx_operator (operator_id),
    KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
经验总结：Docker 里的 MySQL，建表 SQL，要放到 docs/sql/，或者手动进容器执行。别在 DBeaver 里建表，DBeaver 连的是宿主机 MySQL，不是容器 MySQL。

---

问题 48：数据权限，缓存串数据

报错现象：Boss 调 /api/user/page，只看到 2 个用户，不是所有用户。

原因分析：getUserPage 里，cacheKey 是 user:page:1:10，所有用户共用。admin1 先调，缓存了"本部门"的结果；Boss 后调，命中缓存，返回"本部门"。

解决方案：cacheKey 加 userId：

java
String cacheKey = "user:page:" + currentUser.getId() + ":" + pageNum + ":" + pageSize;
经验总结：数据权限 + 缓存，cacheKey 要带 userId。否则，不同用户，共用缓存，数据会串。

---

问题 49：Nacos 启动报 config_info_aggr 表不存在

报错现象：Nacos 容器启动后，浏览器访问 8848 无响应，日志报 Table 'nacos_config.config_info_aggr' doesn't exist。

原因分析：建表 SQL 用了精简版，只建了 config_info 和 his_config_info 两张表，Nacos 2.3.2 需要 12 张表。

解决方案：用 Nacos 官方完整版 mysql-schema.sql，补齐所有表。

经验总结：Nacos 建表 SQL 必须用官方完整版，不能精简。

---

问题 50：Nacos 发布配置报 Field 'nid' doesn't have a default value

报错现象：Nacos 界面"发布失败，请检查参数是否正确"，F12 无响应。命令行调用 API 报 Field 'nid' doesn't have a default value。

原因分析：his_config_info 表的 nid 字段定义为 NOT NULL，但没加默认值。Nacos 2.3.2 插入历史记录时，未传 nid 值，导致 MySQL 报错。

解决方案：ALTER TABLE his_config_info MODIFY COLUMN nid BIGINT NOT NULL DEFAULT 0;

经验总结：Nacos 建表 SQL 必须用官方完整版，官方 SQL 里 nid 有默认值。精简版容易漏字段属性，导致启动能过、写入失败。

---

问题 51：Nacos 删除配置报 Duplicate entry '7' for key 'his_config_info.PRIMARY'

报错现象：Nacos 删除配置时，报主键冲突，删除失败。

原因分析：his_config_info 表的主键 id 定义有问题，Nacos 写历史记录时主键重复。根因是精简版 SQL 字段定义不完整。

解决方案：用 Nacos 官方完整版 mysql-schema.sql 重建所有表。

经验总结：Nacos 建表 SQL 必须用官方完整版。精简版会引发连锁问题：启动报错、写入报错、主键冲突。三个坑，一个根因。

---

问题 52：PowerShell 执行 SQL 报 You have an error in your SQL syntax

报错现象：用 Get-Content 读取 nacos.sql 执行，报 SQL 语法错误，错误信息里出现 ???，中文注释变成乱码。

原因分析：PowerShell 默认用 GBK 编码读文件，nacos.sql 是 UTF-8，中文注释被错误解析。

解决方案：Get-Content 加 -Encoding UTF8，同时 MySQL 客户端加 --default-character-set=utf8mb4。

经验总结：跨平台传 SQL 文件时，编码必须显式指定。PowerShell 读 UTF-8 文件，必须加 -Encoding UTF8。

---

问题 53：Nacos 启动报 Unknown database 'nacos_config'

报错现象：Nacos 容器反复重启，日志报 Unknown database 'nacos_config'。

原因分析：docker-compose down -v 重建后，MySQL 初始化时 nacos.sql 没执行成功，nacos_config 库没建。

解决方案：手动执行 nacos.sql：

powershell
Get-Content docs/sql/nacos.sql -Encoding UTF8 | docker exec -i smart-office-mysql mysql -uroot -p123456 --default-character-set=utf8mb4
执行完确认：

powershell
docker exec -it smart-office-mysql mysql -uroot -p123456 -e "SHOW DATABASES;"
经验总结：Docker 容器的初始化 SQL 执行失败时，不会报错，只会静默跳过。必须手动确认库和表建了没。

---

问题 54：SpringBoot 接 Nacos 后配置拉不到，env.getProperty 全 null

报错现象：SpringBoot 启动报 Could not resolve placeholder 'mqtt.broker'，加调试日志发现 env.getProperty("mqtt.broker") 返回 null。

原因分析：SpringCloud 2023 需要显式引入 spring-cloud-starter-bootstrap 依赖，否则 Nacos 配置不在 Bootstrap 阶段加载。

解决方案：

pom.xml 加 spring-cloud-starter-bootstrap 依赖。

application.yml 只保留最小配置（Nacos 地址 + 应用名 + 环境）。

本地 application-docker.yml 改名 .bak，避免干扰。

经验总结：SpringCloud 2023 + Nacos 配置加载，必须依赖 bootstrap 阶段。没有 bootstrap 依赖，Nacos 配置永远拉不到。

---

问题 55：本地 IDEA 和 Docker 的 SpringBoot 用同一 clientId，MQTT 连接互相挤掉

报错现象：本地启动 SpringBoot 后，日志反复报 MQTT 连接丢失，等待自动重连。

原因分析：本地 IDEA 和 Docker 的 smart-office-app 用同一个 clientId（smart-office-telemetry-subscriber），EMQX 认为它们是同一个客户端，互相挤掉。

解决方案：本地开发的 clientId 加 -local 后缀，在 Nacos 配置里区分：

yaml
mqtt:
  client-id: smart-office-telemetry-subscriber-local
同时停掉 Docker 的 smart-office-app：

powershell
docker stop smart-office-app
经验总结：MQTT clientId 是客户端的唯一标识。多实例部署时，clientId 必须唯一。本地开发和 Docker 环境，clientId 要区分。

---

问题 56：Nacos 动态刷新后，旧 MQTT 连接不断开

报错现象：Nacos 改配置后，@RefreshScope 让 MqttConfig Bean 重建，但旧的 MQTT 连接还挂在 EMQX 上，新连接可能因 clientId 冲突连不上。

原因分析：@RefreshScope 只负责销毁和重建 Bean，但 Bean 销毁时没有主动断开 MQTT 连接。

解决方案：给 MqttConfig 加 @PreDestroy 方法，Bean 销毁前主动断开旧连接：

java
@PreDestroy
public void destroy() {
    if (mqttClient != null && mqttClient.isConnected()) {
        mqttClient.disconnect();
        mqttClient.close();
    }
}
经验总结：动态刷新不只是 @RefreshScope，还要处理 Bean 生命周期。@PostConstruct 管初始化，@PreDestroy 管销毁。连接类 Bean（MQTT、数据库连接池）必须配 @PreDestroy。

面试价值：体现 Bean 生命周期管理（Bean Lifecycle Management）意识

---

问题 57：Docker Compose 引用未声明的网络，报 undefined network

报错信息：service "tdengine" refers to undefined network smart-office-net: invalid compose project

原因分析：tdengine 服务里写了 networks: - smart-office-net，但文件底部的顶层 networks: 块里没有声明 smart-office-net。Compose 规定：服务引用的网络，必须在顶层声明过。

解决方案：

删掉 tdengine 服务的 networks 配置，让它和其他服务一样走默认网络。
或者，在文件底部加 networks: 块，声明 smart-office-net。
经验总结：一份 Compose 文件里的服务，不写 networks 就默认在同一个自定义 bridge 网络里，用服务名互访。没必要给单个服务单独声明网络，除非要做网络隔离。

---

问题 58：Docker 引擎没启动，报 npipe 连接失败

报错信息：failed to connect to the docker API at npipe:////./pipe/dockerDesktopLinuxEngine; check if the path is correct and if the daemon is running

原因分析：Windows 上 Docker 的守护进程走命名管道 npipe:////./pipe/dockerDesktopLinuxEngine，这个管道只在 Docker Desktop 启动后才创建。报"找不到文件"就是引擎没起。

解决方案：

开 Docker Desktop，等托盘鲸鱼图标稳定。
docker version 确认 Server 段有输出。
再执行 docker compose 命令。
经验总结：docker compose 命令连不上引擎时，先确认 Docker Desktop 在跑，而不是查 compose 文件。

---

问题 59：TDengine 原生连接报 no taos in java.library.path

报错信息：java.lang.UnsatisfiedLinkError: no taos in java.library.path ... at com.taosdata.jdbc.TSDBJNIConnector.<clinit>

原因分析：用了原生连接 jdbc:TAOS://，它依赖本地 C 动态库（Windows 上是 taos.dll）。本地没装 TDengine 客户端，JVM 找不到 taos.dll。

解决方案：

改成 REST 连接：jdbc:TAOS-RS://localhost:6041/smart_office。
或者，装 Windows 版 TDengine 客户端，把 taos.dll 放到 java.library.path。
经验总结：TDengine 有两种 JDBC 连接——原生（TAOS，需本地库）和 REST（TAOS-RS，走 taosAdapter）。容器化场景推荐 REST，不用给每个客户端装本地库。

---

问题 60：改了配置没生效，TDengine URL 实际值还是旧的

报错现象：本地改了 application.yml 的 tdengine.url，重启后报错没变。

原因分析：

Nacos 配置优先级高于本地 application.yml，运行时读的是 Nacos 的 smart-office-docker.yml。
当前激活的 profile 是 docker，配置来自 Nacos 对应 DataId。
解决方案：

在 TdengineWriteService.init() 里加 log.info(">>> TDengine URL 实际值: {}", url)，打印运行时真实值。
改 Nacos 控制台里对应 DataId 的配置，或改对 profile。
经验总结：多源配置（本地 + Nacos + profile）下，改了没生效，先打印"运行时实际值"，别猜改了哪份。Nacos 优先于本地。

---

问题 61：TDengine 双写少数据，因 reportTime 为 null

报错现象：压测 5 万条，MySQL 5 万，TDengine 只有 3.1 万，少 36%。搜不到 "TDengine 双写失败" 日志。

原因分析：

reportTime 依赖 MyBatis-Plus 的 @TableField(fill = FieldFill.INSERT) 自动填充。
自动填充只在 MyBatis 插入时生效，不保证回填到内存对象。
tdengineWriteService.batchInsert(batch) 读到内存对象 reportTime 为 null，拼 SQL 时 NPE，整批 TDengine 写入失败。
异常被 flush 的 catch 吃掉记死信，所以没报错日志，数据静默丢。
解决方案：在 parseAndCheckIdempotent 里显式设置：

telemetry.setReportTime(java.time.LocalDateTime.now());
经验总结：双写场景，依赖框架自动填充的字段，旁路写入可能拿到未填充的对象。跨模块共用实体时，字段要显式设置，不依赖框架副作用。

---

问题 62：TDengine 多子表合并 INSERT 语法错误

报错现象：满屏 DuplicateKeyException，TDengine 写入全失败。合并 INSERT 拼的 SQL 每个子表都带 INSERT INTO。

原因分析：TDengine 多子表 INSERT 的正确语法是：只有第一个子表带 INSERT INTO，后续子表直接写子表名。每个都写 INSERT INTO，是三条独立语句，TDengine 不认。

解决方案：

if (first) {
    sql.append("INSERT INTO ").append(subTable).append(" USING telemetry TAGS (...) VALUES ");
    first = false;
} else {
    sql.append(" ").append(subTable).append(" USING telemetry TAGS (...) VALUES ");
}
经验总结：TDengine 的多子表 INSERT 语法，只有第一个带 INSERT INTO。拼 SQL 时用 first 标记区分。

---

问题 63：死信表 message_id 重复，主键冲突

报错现象：满屏 DuplicateKeyException: Duplicate entry '004:74.0' for key 'mqtt_message_log.uk_message_id'

原因分析：

死信用 deviceId + ":" + temperature 当 message_id。
温度只有 20 个值（70 + i%20），反复出现，message_id 重复。
死信表 message_id 有唯一约束，第二次就冲突。
更严重：DeadLetterService.record 把异常抛出去了，死信记录失败反而影响主流程。
解决方案：

message_id 改用 topic + ":" + System.nanoTime()，保证唯一。
DeadLetterService.record 内部 catch 所有异常，不抛。
经验总结：死信记录本身不能成为新的故障源。message_id 必须真正唯一；记录失败只打日志，绝不抛异常影响主流程。

---

问题 64：@Slf4j 的 log 和字段 log 冲突

报错信息：Not generating field 'log': A field with same name already exists

原因分析：类上加了 @Slf4j，Lombok 会自动生成一个 log 字段。但类里已经有手写的 log 字段（如 private MqttMessageLog log;），同名冲突，Lombok 不生成，log.error() 就报错。

解决方案：把自定义字段改名（如 messageLog），log 这个名字留给 Lombok 的 Logger。

经验总结：@Slf4j 的 log 是保留名，类里的其他字段别叫 log。

---

问题 65：本地 IDEA 和 Docker 同时跑 SpringBoot，MQTT 反复断连

报错现象：IDEA 日志反复报 MQTT 连接丢失，EOFException，reconnect=true。

原因分析：Docker 里的 smart-office 容器和 IDEA 里的 SpringBoot 同时跑，两个实例都用同一个 clientId 连 EMQX，EMQX 互踢，来回重连。

解决方案：

二选一：要么只跑 IDEA（停容器 docker compose stop smart-office），要么只跑容器。
长期方案：clientId 加环境后缀（-local / -docker）区分。
经验总结：SpringBoot 实例只能有一个。本地开发和容器部署不能同时启用应用实例，否则抢 clientId、抢端口、抢资源。

---

问题 66：Nacos 未就绪，SpringBoot 启动报 BeanCreationException

报错现象：SpringBoot 启动报 UnsatisfiedDependencyException / BeanCreationException，一路连锁到 ConnectException: localhost:9848。

原因分析：

Nacos 容器刚启动 30~60 秒内还没就绪（要连 MySQL 初始化）。
SpringBoot 在 Nacos 没就绪时启动，拉配置失败，上下文初始化失败。
日志里能看到 "Fail to connect server, after trying N times"。
解决方案：

等 Nacos 日志出现 "Nacos started successfully" 再启动 SpringBoot。
或在 docker-compose 里给 nacos 加 healthcheck，smart-office 依赖 condition: service_healthy。
经验总结：Nacos 是 SpringBoot 的启动依赖（bootstrap 阶段就要连），不是可选组件。Nacos 没就绪，应用起不来。

---

问题 67：容器全停后，IDEA 里应用连不上一切

报错现象：IDEA 里 SpringBoot 报 Redis / Nacos / EMQX 全部 Connection refused。

原因分析：执行了 docker compose down（或关了 Docker Desktop），Docker 里所有容器（MySQL、Redis、EMQX、Nacos、TDengine）一起停。IDEA 里的 SpringBoot 连不上中间件。

解决方案：重新起中间件（不带 smart-office）：

docker compose up -d mysql redis rabbitmq emqx nacos tdengine prometheus grafana
经验总结：分清"停某个服务"和"停全部"。docker compose stop <服务名> 停单个；docker compose down 停全部并删容器。本地开发时，中间件用 Docker 起，应用用 IDEA 跑，二选一跑应用实例。

---

问题 68：EMQX 5.x Dashboard 密码命令变更

报错现象：改 Dashboard 密码总"改了又失效"。

原因分析：旧版命令 emqx ctl dashboard passwd 在 EMQX 5.x 已不存在，改用 emqx ctl admins passwd。用旧命令，看起来执行了、实际没生效。

解决方案：

docker exec -it smart-office-emqx emqx ctl admins passwd admin admin123456
经验总结：EMQX 5.x 的 Dashboard 用户管理命令是 admins（add / passwd / del / mfa），不是 dashboard。改配置/密码没生效时，先确认命令版本对不对。


## 多模块重构 + 工业级数据链路（Day 2）

### 问题 69：多模块 Maven 重构后，simulation 模块 BUILD FAILURE

**报错信息：** `Failed to execute goal spring-boot-maven-plugin:3.3.5:repackage ... Unable to find main class`

**原因分析：** 新建的 `smart-office-simulation` 模块**没有 Java 源码**（空模块），但 pom 里配了 `spring-boot-maven-plugin`，打包插件找不到主类。

**解决方案：**
- 给 simulation 模块加启动类 `SimulationApplication`
- 或临时在父 pom 的 `<modules>` 里注释掉 simulation 模块

**经验总结：** 空模块不要配打包插件，要么先写代码，要么从父 pom 的 modules 里排除。

---

### 问题 70：多模块重构后，Jackson 依赖缺失

**报错信息：** `程序包com.fasterxml.jackson.databind不存在` / `找不到符号: 类 ObjectMapper`

**原因分析：** `smart-office-simulation` 用了 `spring-boot-starter`（非 web），它**不包含 Jackson**。而 `TelemetryPublisher` 用 `ObjectMapper` 序列化 JSON，缺依赖。

**解决方案：** simulation 的 pom 加：

```xml
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
</dependency>
经验总结： 最小依赖原则——非 web 模块用 JSON 要手动加 jackson-databind，不要引入整个 spring-boot-starter-web（会带 Tomcat）。

问题 71：Maven 打包时缺 project.build.sourceEncoding 导致文件乱码
报错信息： MalformedInputException: Input length = 1，应用启动失败。

原因分析：

父 pom 的 <properties> 没有 project.build.sourceEncoding=UTF-8

Maven 编译时用平台默认编码（Windows = GBK）

YAML 配置文件的中文注释被按 GBK 读，解析失败

Nacos 配置加载链断掉，后续所有配置拉不到

解决方案：

父 pom 加：

xml
<properties>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <project.reporting.outputEncoding>UTF-8</project.reporting.outputEncoding>
</properties>
关键配置文件（application.yml / bootstrap.yml）去掉中文注释，防患未然

经验总结： Maven 的 project.build.sourceEncoding 是必须显式声明的。Windows 环境下默认 GBK，UTF-8 项目会踩坑。

问题 72：TDengine 超表不存在，双写 100% 失败
报错信息：

text
java.sql.SQLException: TDengine ERROR (0x2603): sql: INSERT INTO d_xxx USING telemetry TAGS ('xxx') VALUES ...
desc: Table does not exist
死信表在几分钟内涨到 6000+ 条，但 MySQL 主链路正常。

原因分析：

TDengine 的超表 telemetry 没建（清库时 DROP DATABASE 后没重建）

CREATE TABLE ... USING telemetry 要求超表已存在

每条 INSERT 都失败 → 进死信 → 死信暴增

解决方案：

应急： 手动建超表

sql
CREATE STABLE IF NOT EXISTS telemetry (ts TIMESTAMP, temperature DOUBLE) TAGS (device_id NCHAR(32));
永久： TdengineWriteService.init() 里加自动建超表逻辑

java
@PostConstruct
public void init() throws SQLException {
    conn = DriverManager.getConnection(url, username, password);
    try (Statement stmt = conn.createStatement()) {
        stmt.executeUpdate("CREATE STABLE IF NOT EXISTS telemetry (...) TAGS (...)");
    }
}
经验总结： 时序库的"超表"是子表的前置依赖。清库重建后，必须自动建超表，否则旁路全丢。旁路失败被异常隔离，主链路感知不到——这是双写设计的隐患，必须主动监控死信表。

问题 73：TDengine 多子表合并 INSERT，首批大批量建表失败
报错信息： Table does not exist（同上，但根因不同）。

原因分析：

TDengine 支持 INSERT INTO 子表 USING 超表 TAGS(...) 隐式建子表

一批 INSERT 涉及 1000 个"首次写入的子表" 时，TDengine 3.3.0 处理不过来

换成"先显式建子表，再纯 INSERT"就稳定了

解决方案：

拆分两步：ensureSubTables() 预建子表（幂等，CREATE TABLE IF NOT EXISTS）

加 createdSubTables 缓存，同一子表只建一次

batchInsert() 里 INSERT 不带 USING

经验总结： "隐式建表"是甜点，不是主食。高并发 / 大批量场景要"显式建表 + 缓存已建表"，把不确定性降到最低。

问题 74：MQTT cleanSession=true 导致 app 掉线期间数据全丢
报错现象：

app 启动前的数据全没写入

EMQX 里 clean_start=true，session_expiry_interval=0

app 掉线后，EMQX 立即销毁会话

app 重连 → 从零开始收新数据

原因分析：

MqttConnectOptions.setCleanSession(true) → 每次连接都是全新会话

EMQX 不为该 clientId 缓存离线消息

MQTT 持久会话的核心参数，很多人不知道

解决方案：

java
MqttConnectOptions options = new MqttConnectOptions();
options.setClientId("smart-office-backend");   // 固定 clientId
options.setCleanSession(false);                // ★ 关键：持久会话
options.setAutomaticReconnect(true);
options.setConnectionTimeout(30);
options.setKeepAliveInterval(60);
⚠️ 常见错误：代码里有两处 setCleanSession，后面那行 true 覆盖了前面的 false。 只留一行。

经验总结： cleanSession 是 MQTT 3.1.1 最核心的参数：

true = 每次全新会话，掉线丢消息

false = 持久会话，Broker 缓存离线消息，重连补发

验证方式：docker exec smart-office-emqx emqx ctl clients show smart-office-backend，看 clean_start 和 session_expiry_interval。

问题 75：MQTT 回调里做 IO 阻塞，ACK 推不出去
报错现象：

app 掉线期间 EMQX 缓存 3200 条

app 重连后，EMQX 推了 32 条就停了

inflight=32 一直不变，enqueued_msgs=3168 不动

MySQL 只进了 96 条

原因分析：

Paho MQTT 的回调线程只有 1 个（CommsReceiver）

messageArrived 里 log.info("收到 MQTT 消息...") 每条都打日志 → 日志 IO 阻塞回调线程

app ACK 推不出去 → EMQX inflight 满 → 后续消息不推

解决方案：

回调里只入内存队列，零 IO

java
@Override
public void messageArrived(String topic, MqttMessage message) {
    String payload = new String(message.getPayload());
    telemetryQueue.offer(topic, payload);   // 只入队，不打日志
}
日志移到队列消费端（TelemetryBatchWriter）

调大 Paho setMaxInflight(1000)（默认 10 或 30）

经验总结： MQTT 回调是单线程的，回调里做慢操作（日志、Redis、HTTP）会阻塞整个接收链路。正确做法：回调里只入队，异步消费。

问题 76：EMQX 离线队列默认 1000 条，长时间离线丢数据
报错现象： EMQX Dashboard 显示"消息丢弃"峰值。

原因分析： EMQX max_mqueue_len 默认 1000。app 掉线期间，sim 发超过 1000 条，多的直接丢。

解决方案： deploy/docker-compose.yml 的 emqx 服务加：

yaml
environment:
  - EMQX_MQTT__MAX_MQUEUE_LEN=100000
或通过 EMQX Dashboard → 管理 → 配置 → MQTT 直接改。

经验总结： 工业级"数据零丢失"必须调大 offline 队列。开发环境默认 1000 够用，生产环境必须按"最长离线时间 × 消息速率"估算。

数据零丢失验证结果（Day 2 收官）
场景： 100 台设备，每秒 100 条遥测，app 掉线 30 秒。

检查点	结果
EMQX clean_start	false（持久会话）
EMQX session_expiry_interval	7200（2 小时）
app 掉线时 enqueued_msgs	3200（离线队列积压）
app 重连后 enqueued_msgs	0（补发完成）
app 重连后 delivered_msgs	3200
MySQL 主表	3262
TDengine 超表	3166
死信	0
补发入库耗时	< 1 秒
结论： MQTT 持久会话 + offline 队列补发 + MySQL/TDengine 双写，端到端数据零丢失。


## 问题 77：Docker 迁移后端口偏移，客户端连不上

**现象**：Docker Desktop 数据从 C 盘迁到 D 盘后，DataGrip 连 MySQL 报 "Communications link failure"，但容器显示 `Up (healthy)`。

**根因**：Docker Compose 端口映射配置为 `3307:3306`，宿主机端口是 3307，不是默认 3306。客户端连默认端口失败。

**解决**：
1. 客户端改用宿主机端口（MySQL 3307、Redis 6380、RabbitMQ 5673、EMQX 1884）
2. `docker port <container>` 确认实际映射
3. `Test-NetConnection` 验证端口可达

**面试话术**：Docker 端口映射格式 `宿主机端口:容器端口`，多实例部署时常用端口偏移避免冲突，排查时要分清"宿主机端口"和"容器内端口"。


## 问题 78：MQTT 持久会话导致路由表错乱（核心坑）

**现象**：
- 仿真层在发（EMQX 流入 103 msg/s）
- app 订阅记录在（Subscriptions 表显示 `smart-office-backend` 订阅 `factory/device/+/telemetry`）
- **但 EMQX 流出 0，app 收不到任何消息**
- MQTTX 手动发 9 条，自己订阅都收不到

**根因**：
- app 用 `cleanSession=false` + 固定 clientId `smart-office-backend`
- app 频繁重启（改代码调试），每次用同一 clientId 重连
- EMQX 保留旧会话 2 小时（`session_expiry_interval=7200`）
- **旧会话的订阅残留在 EMQX 路由表里，新连接的消息被投递给"已断开的旧连接"**
- 结果：订阅看起来在、消息看起来有、但投递目标是死的

**解决**：
1. **短期**：EMQX Dashboard → Clients → 踢掉"未连接"的僵尸客户端
2. **根治**：改 `cleanSession=true`（放弃离线补发），或 clientId 加时间戳（每次新连接）
3. **生产环境**：用固定 clientId + `cleanSession=false`，但需在应用关闭时**主动 disconnect + 清理会话**

**面试话术**：MQTT 持久会话机制下，`clientId` 是会话的唯一标识。频繁重启 + 不清理旧会话，会导致 EMQX 路由表指向死连接，消息投递失败但监控指标显示"订阅存在"。解决方式是根据业务选择 `cleanSession` 策略，并配合会话生命周期管理。


## 问题 79：MQTT 扩字段后 NULL 值

**现象**：`device_telemetry` 表加了 `vibration/speed/state` 三列后，新数据三列都是 NULL。

**根因**：
1. 仿真层 payload 确实有这三个字段（`TelemetrySample` 完整序列化）
2. 但 app 端解析逻辑没同步更新（`parseAndCheckIdempotent` 没 set 新字段）
3. `insertBatch` XML 没加新列
4. 实体类没加新字段

**解决**：四处都要改
1. 实体 `DeviceTelemetry` 加字段
2. `parseAndCheckIdempotent` 解析 payload 新字段
3. Mapper XML `insertBatch` 加列
4. 数据库 `ALTER TABLE` 加列

**面试话术**：字段变更要保证"实体-DAO-SQL-数据库"四处一致，否则会出现"数据写入成功但字段为 NULL"的隐蔽 bug。


## 问题 80：AI Agent 异步处理抛 ConcurrentModificationException

**现象**：AI Agent 处理批量遥测数据时，抛 `java.util.ConcurrentModificationException`，堆栈指向 `AiMonitorService.onBatchFlushed(AiMonitorService.java:52)`。

**根因**：
- `AiMonitorService.onBatchFlushed()` 加了 `@Async`，异步执行
- 主线程 `TelemetryBatchWriter.flush()` 调用后立即 `batch.clear()`
- 异步线程还在遍历同一个 `List` 引用 → 迭代器失效

**解决**：
1. 异步方法入口做防御性拷贝：`List<DeviceTelemetry> snapshot = new ArrayList<>(batch);`
2. **关键**：`for` 循环遍历 `snapshot`，不是 `batch`（拷贝后必须用新集合）
3. 常见坑：拷了 `snapshot` 但 `for` 循环还是 `batch`，白拷

**面试话术**：`@Async` 方法拿到的是引用传递，主线程如果在异步执行期间修改集合（如 `batch.clear()`）会抛 CME。工业级做法是在异步边界做防御性拷贝，且遍历时必须用拷贝后的集合。

**代码**：
```java
@Async
public void onBatchFlushed(List<DeviceTelemetry> batch) {
    if (batch == null || batch.isEmpty()) return;
    List<DeviceTelemetry> snapshot = new ArrayList<>(batch);   // ★ 拷贝
    for (DeviceTelemetry t : snapshot) {   // ★ 遍历拷贝
        // ...
    }
}
问题 81：MySQL JSON 字段写入非法 JSON
现象：

text
Data truncation: Invalid JSON text: "Invalid value." at position 1 
in value for column 'ai_analysis_log.tool_calls'
根因：

ai_analysis_log.tool_calls 字段是 MySQL JSON 类型

手拼 JSON："[send_alert(id=" + alertId + ")]"

这不是合法 JSON（缺引号、缺冒号）

解决：用 Jackson 序列化对象，不手拼字符串。

java
List<Map<String, Object>> toolCallsList = new ArrayList<>();
if (alertId != null) {
    Map<String, Object> call = new HashMap<>();
    call.put("tool", "send_alert");
    call.put("alertId", alertId);
    toolCallsList.add(call);
}
logEntity.setToolCalls(objectMapper.writeValueAsString(toolCallsList));
// 输出：[{"tool":"send_alert","alertId":123}] —— 合法 JSON
面试话术：MySQL 的 JSON 类型字段会严格校验格式，非合法 JSON 直接拒绝。工业级做法是用 Jackson/Gson 序列化对象生成 JSON，不手拼字符串——手拼容易漏引号、逗号，难以维护。

问题 82：Spring AMQP 默认转换器不支持 POJO
现象：

text
java.lang.IllegalArgumentException: SimpleMessageConverter only supports 
String, byte[] and Serializable payloads, received: 
com.example.smart_office.entity.ai.AlertRecord
根因：

Spring AMQP 默认用 SimpleMessageConverter

它只支持 String、byte[]、Serializable 三种类型

直接传 POJO（AlertRecord）→ 类型不匹配 → 抛异常

解决：

方案 A（推荐）：发 JSON 字符串

java
String json = objectMapper.writeValueAsString(record);
rabbitTemplate.convertAndSend(exchange, routingKey, json);
方案 B：让 AlertRecord 实现 Serializable（不推荐，跨语言差）

为什么推荐 JSON：

跨语言兼容（未来 Python/Go 消费者也能读）

RabbitMQ 管理台可读（能看到明文）

字段增删无需改序列化机制

面试话术：Spring AMQP 默认 SimpleMessageConverter 只支持 String/byte[]/Serializable。工业级做法是发 JSON 字符串，好处是跨语言兼容、管理台可读、字段演进灵活。或配置 Jackson2JsonMessageConverter 做全局转换。

问题 83：告警风暴（同一设备重复告警）
现象：

一台设备持续异常，ai_analysis_log 里同一设备有 10 条记录

alert_record 里同一设备有 10 条告警

日志刷屏、MySQL 刷屏、RabbitMQ 刷屏

根因：

规则引擎无去重，每条数据独立判断

设备持续异常时，每批数据（1 秒 1 批）都命中同一规则

结果：1 分钟产生 60 条重复告警

解决：Redis SETNX + TTL（5 分钟）做去重。

java
String dedupKey = "ai:agent:dedup:" + deviceId + ":" + ruleName;
Boolean isFirst = redisTemplate.opsForValue()
        .setIfAbsent(dedupKey, "1", Duration.ofMinutes(5));
if (Boolean.FALSE.equals(isFirst)) {
    continue;   // 5 分钟内已处理，跳过
}
语义：

首次命中 → SETNX 成功 → 处理 + 告警

5 分钟内重复命中 → SETNX 失败 → 跳过

5 分钟后 → TTL 过期 → 允许重新触发（应对"恢复后再次异常"）

TTL 选择依据：

太长 → "恢复后再次异常"无法触发

太短 → 告警仍风暴

5 分钟是工业场景经验值（覆盖一次典型故障演化周期）

面试话术：工业告警系统必须有"去重冷却"机制，避免告警风暴。Redis SETNX + TTL 实现分布式去重，key 是 ai:agent:dedup:{deviceId}:{ruleName}，TTL 5 分钟。这样既避免刷屏，又保证"恢复后再次异常"能重新触发。

问题 84：规则引擎覆盖不全（数据追溯发现）
现象：注入 10 个故障，AI Agent 只处理 8 台设备。CONVEYOR-01-01 和 ASSEMBLY-01-01 无记录。

根因（设备级追溯）：

CONVEYOR-01-01 注入 BEARING_WEAR（轴承磨损）：振动上升慢，测试时间内未达 VIB_GT_10（10mm/s）阈值；温度也没到 85℃

ASSEMBLY-01-01 注入 SUDDEN_FAILURE（突发断裂）：状态瞬变未触发已有规则

规则引擎的三个设计缺口：

振动早预警缺失：没有 VIB_GT_5（WARNING）这样的早期预警规则

趋势异常检测缺失：只看单点阈值，没有"温度连续 N 次上升 > X℃"这样的趋势判断

瞬时故障捕获缺失：短时间状态变化（如 RUNNING → FAULT → RUNNING）难以捕获

解决方向：

补规则：VIB_GT_5 → WARNING，让振动异常更早被捕获

补趋势规则：滑动窗口，检测"3 分钟温度上升 > 5℃"

补瞬时规则：记录状态变化历史，检测短时间内的状态迁移

关键认知：

故障注入 ≠ 一定触发规则：仿真层故障演化有时间维度，短时间内可能没到阈值

端到端测试的局限：依赖物理演化，时间长且不稳定

单元测试的必要性：构造边界值数据，毫秒级验证规则逻辑

面试话术：我做数据一致性追溯时发现，注入 10 个故障，AI Agent 只处理了 8 个。追溯发现是 BEARING_WEAR（振动缓慢上升）和 SUDDEN_FAILURE（瞬态）在测试时间内未达阈值。这暴露了规则引擎的三个可优化方向：补振动早预警、加趋势检测、捕获瞬时故障。这是"用数据驱动规则迭代"的工程思路——不是设计时拍脑袋，而是从实际数据中发现规则覆盖缺口。

数据对账（v1.6 AI Agent 验证）
场景：注入 10 个故障，观察 AI Agent 处理。

对账结果：

规则	级别	数量	是否发告警
TEMP_GT_85	WARNING	8	❌
TEMP_GT_95	CRITICAL	8	✅
STATE_FAULT	CRITICAL	8	✅
合计		24	16 发告警
核对：

ai_analysis_log = 24 条

alert_record = 16 条（8 TEMP_GT_95 + 8 STATE_FAULT）

差值 = 8（WARNING 级别） ✅

结论：告警分级逻辑正确，数据一致且可追溯。

面试价值：体现"分层对账"思维——数据源层（故障注入数）→ 处理层（规则命中数）→ 输出层（告警数），三层逐层对账，任何一层出现差异都能定位到具体环节。