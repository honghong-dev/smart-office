# Smart-Office 架构与原理笔记

本文档记录项目开发、部署、工业物联网接入、配置管理、监控体系过程中涉及的核心原理与底层逻辑，是"为什么这么做"的答案。

---

## 一、一次请求的完整链路

```text
Apifox → 宿主机端口 → Docker 端口映射 → 容器内 SpringBoot
→ JwtInterceptor → Controller → Service → Mapper → MySQL
→ Redis（缓存）
→ 返回响应
二、端口映射原理
命令：docker run -d -p 8081:8080 ...

右边 8080：容器内部端口（固定，SpringBoot 监听）。

左边 8081：宿主机端口（可自定义，不能与已有服务冲突）。

访问时用宿主机端口（8081），而不是容器端口（8080）。

三、容器网络隔离
容器内的 localhost 指向容器自己，不是宿主机。

容器要访问宿主机服务，必须使用宿主机真实 IP 或 host.docker.internal。

Docker Compose 内部：服务之间，用服务名通信，比如 mysql、redis、rabbitmq、emqx、nacos、tdengine。

四、MySQL 8.0 加密方式兼容问题
MySQL 8.0 默认使用 caching_sha2_password。

旧版 JDBC 驱动不认识这种新加密方式，导致连接被拒。

解决方案：ALTER USER 'root'@'%' IDENTIFIED WITH mysql_native_password BY '密码';

这只影响"连接协议"，不影响业务层面的 BCrypt 密码加密。

五、RabbitMQ 权限模型
用户必须被授予 vhost 的访问权限，否则报 NOT_ALLOWED。

在管理后台 Admin → Users 中点击 Set permission，填入 .* 即可。

六、防火墙与安全
本地开发可临时关闭防火墙，但不能长期裸奔。

正确做法：开放白名单（如 Docker 网段 172.31.0.0/16）。

生产环境：数据库/Redis/RabbitMQ 端口绝对不开放公网，只允许内网访问。

密码必须复杂：长度 ≥16，大小写 + 数字 + 特殊符号。

应用连接数据库使用最小权限用户，不使用 root。

七、本地访问 vs 公网访问
本地：localhost 或局域网 IP。

公网：需要租云服务器 + 域名 + Nginx 反向代理 + HTTPS 证书。

Nginx 负责转发请求到 8080，对外只暴露 80/443。

八、关键结论
端口映射：右边固定，左边可自定义，避开冲突。

容器连宿主机：不能用 localhost，要用真实 IP 或服务名。

MySQL 8.0：必须改加密方式兼容旧驱动。

安全上线：密码必须复杂，数据库端口必须内网隔离。

九、Docker 镜像与容器
镜像（Image）：静态的打包文件，包含代码、JDK、环境。由 docker build 生成。

容器（Container）：镜像的运行实例。由 docker run 启动。

仓库（Registry）：存放镜像的地方，如 Docker Hub。

Dockerfile：描述如何构建镜像的脚本。

docker-compose.yml：编排多个容器的配置文件。

十、Docker Compose 一键部署
核心思想
用 docker-compose.yml 编排所有服务，一条命令 docker compose up -d，一键启动。

服务清单
服务	镜像	宿主机端口	容器端口
mysql	mysql:8.0	3307	3306
redis	redis:latest	6380	6379
rabbitmq	rabbitmq:3.13-management	5673 / 15673	5672 / 15672
emqx	emqx/emqx:latest	1884 / 18084 / 8084	1883 / 18083 / 8083
nacos	nacos/nacos-server:v2.3.2	8848 / 9848 / 9849	8848 / 9848 / 9849
tdengine	tdengine/tdengine:3.3.0.0	6030 / 6041	6030 / 6041
smart-office	smart-office-smart-office	8081	8080
prometheus	prom/prometheus:latest	9090	9090
grafana	grafana/grafana:latest	3000	3000
环境变量覆盖
在 docker-compose.yml 里，用 environment 覆盖配置：

yaml
smart-office:
  environment:
    - SPRING_PROFILES_ACTIVE=docker
    - MYSQL_HOST=mysql
    - REDIS_HOST=redis
    - RABBITMQ_HOST=rabbitmq
    - MQTT_HOST=emqx
    - TDENGINE_HOST=tdengine
    - SPRING_CLOUD_NACOS_CONFIG_SERVER_ADDR=nacos:8848
SpringBoot 的"宽松绑定"：MQTT_HOST → ${MQTT_HOST:localhost}。环境变量优先级最高。

healthcheck
用 healthcheck 确保 MySQL 就绪后，再启动 SpringBoot。避免"项目启动 ≠ 依赖就绪"。

十一、RBAC 权限模型
详细设计见 RBAC_DESIGN.md，此处只记录核心原理。

数据模型
text
sys_user → sys_user_role → sys_role → sys_role_permission → sys_permission
多对多关系：一个用户可以有多个角色，一个角色可以有多个权限。

四种角色（三级管理）
角色	权限	能改谁
SUPER_ADMIN	所有权限	除自己外所有人，不能造第二个老板
ADMIN	所有权限	除老板外所有人，能造厂长
SUB_ADMIN	有 user:assignRole 权限，业务限制只能改员工	只能改员工，能把员工提拔为班长
USER	基础权限	不能改任何人
权限校验流程
用户登录成功后，根据 userId 查询权限编码列表。

将权限列表写入 Redis（Key: user:permissions:{userId}，30 分钟过期）。

每次请求进入 JwtInterceptor：

校验 JWT 是否有效。

检查方法上是否有 @RequiresPermission 注解。

从 Redis 读取用户权限，比对是否包含所需权限。

缓存未命中时，查数据库，重建缓存（用户无感，不用重新登录）。

无权限返回 403。

接口级别鉴权通过自定义注解 + 反射实现。

为什么用 Redis 缓存权限？
避免每次请求都查数据库，减少压力。

权限变更时，清空 Redis 即可让用户重新拉取最新权限。

30 分钟过期，兼顾性能与安全性。

十二、JWT 鉴权规范：Bearer 前缀
HTTP 标准（RFC 6750）规定，Bearer Token 必须放在 Authorization 头，格式为 Bearer <Token>。

后端拦截器必须严格校验 Bearer 前缀，不符合的一律 401。

这是一种"严格模式"，比"宽容模式"（兼容不加前缀）更安全、更规范。

十三、审批流设计
详细设计见 PROGRESS.md，此处只记录核心原理。

延迟队列设计
text
审批提交 → approval.delay.queue（TTL = 30分钟）
  ↓ 30分钟后，消息过期
自动进入 approval.dlx.queue（死信队列）
  ↓
消费者收到死信 → 查审批单状态 → 未处理 → 发提醒
关键点：

approval.delay.queue：没有消费者，消息只进不出，等 TTL 过期。

approval.dlx.queue：有消费者，处理过期消息。

队列属性不能热更新：改 TTL，要删队列，重启 SpringBoot，重建。

消费端幂等：RabbitMQ 的投递语义是"至少一次"，消费端用 Redis SETNX 去重，key 是 approval:timeout:{approvalId}。

设计亮点：

用 @Transactional 保证数据一致性。

用 @RequiresPermission 控制接口权限。

用 TransactionSynchronizationManager.afterCommit() 保证"事务提交后才发消息"。

用 RabbitMQ 延迟队列解耦"提交审批"和"超时提醒"。

十四、分布式锁（Redisson）
解决什么问题
同一审批单，多个审批人同时操作，数据不一致。

实现
java
RLock lock = redissonClient.getLock("approval:lock:" + approvalId);
try {
    boolean locked = lock.tryLock(5, 10, TimeUnit.SECONDS);
    if (!locked) {
        throw new RuntimeException("审批单正在处理中，请稍后重试");
    }
    // 业务逻辑：查状态、改状态、插记录
} finally {
    if (lock.isHeldByCurrentThread()) {
        lock.unlock();
    }
}
双保险
锁：同一时刻，只有一个请求能处理。

状态校验：拿到锁后，再查审批单状态，已处理则跳过。

十五、权限缓存刷新（Redis Pub/Sub）
解决什么问题
管理员改权限后，用户不重新登录，权限不生效。

实现
text
管理员改权限 → 发消息到 permission:refresh 频道
  ↓
各 Java 实例订阅频道，收到消息 → 清掉相关用户的权限缓存
  ↓
下次请求，缓存未命中 → 重新查数据库 → 写入缓存
双刃剑
好处：权限变更秒级生效，用户无感。

坏处：多实例订阅，消息可能重复；要处理消息丢失。

十六、审计日志
解决什么问题
记录"谁改了什么、什么时候改的"。

实现
加一张 sys_audit_log 表。

关键操作（assignRole、handleApproval）调 AuditLogService.record()。

记录：操作人、操作类型、目标、旧值、新值、IP、时间。

设计亮点
try-catch 包裹：审计日志失败，不影响主流程。

独立 Service：AuditLogService，可复用。

十七、数据权限
解决什么问题
控制"能看哪些数据"，不只是"能不能看"。

实现
加 sys_dept 表，sys_user 表加 dept_id。

查询时，如果是非管理员，加 WHERE dept_id = 当前用户的部门。

缓存 Key 要带 userId
java
String cacheKey = "user:page:" + currentUser.getId() + ":" + pageNum + ":" + pageSize;
如果 cacheKey 不带 userId，admin1 和 Boss 会共用同一个缓存，数据会串。

注册 vs 定岗
注册，只分配 USER 身份。部门、岗位，需要管理员手动分配。这是真实企业流程。

十八、工业物联网数据链路（MQTT）
架构组成
设备（Publisher）：模拟设备，用 MQTTX 或 Java 脚本发布数据。

Broker（EMQX）：MQTT 消息代理服务器。

后端（Subscriber）：Java 后端通过 Paho MQTT 客户端订阅 Topic。

数据链路
设备向 factory/device/001/temperature 发布 JSON 数据，QoS 1。

EMQX 接收并转发给所有订阅了 factory/device/+/temperature 的客户端。

Java 后端 messageArrived 回调收到消息，只做入队（TelemetryQueue.offer），不碰数据库。

TelemetryBatchWriter 用 4 线程并行消费，每攒够 1000 条或超时 1 秒，批量 INSERT 到 device_telemetry。

入库前用 Redis SETNX 做幂等校验（key：mqtt:idempotent:{deviceId}:{timestamp}）。

温度 > 80℃ 时，按批次统计告警数量，不逐条打印。

解析失败、入库失败的消息进死信队列（DeadLetterService），不丢弃。

通配符说明
+：单层通配符。factory/device/+/temperature 匹配 factory/device/001/temperature。

#：多层通配符。factory/# 匹配 factory 下所有层级。

QoS 等级对比
QoS	语义	丢消息	重复	握手次数	速度	适用场景
0	最多一次	可能	不会	1	最快	传感器周期上报
1	至少一次	不会	可能	2	中	工业数据采集
2	恰好一次	不会	不会	4	最慢	金融交易
本项目选 QoS 1 + 应用层幂等：QoS 1 保证不丢，Redis 幂等保证不重，效果上等于 QoS 2，但吞吐量高得多。这叫"效果上恰好一次"（Effectively Once）。

持久会话
cleanSession=false + 固定 clientId：EMQX 为掉线的订阅端暂存消息，订阅端重连后补发。

会话过期时间：本项目配 2 小时。超过后，EMQX 清掉会话和暂存消息。

补发的双刃剑：好处是不丢消息；坏处是重连时一次性推大量消息，消费端可能扛不住。

EMQX 消息丢弃的常见原因
无订阅者：订阅端掉线瞬间，EMQX 认为该 topic 没有订阅者，直接丢弃。

离线队列满：max_mqueue_len 超限，超出部分丢弃。

飞行窗口满：max_inflight 超限，QoS 1 消息在途过多，暂停投递或丢弃。

消息过期：超过 message_expiry_interval，丢弃。

判断丢没丢，看 EMQX Dashboard 的"消息丢弃"指标。0 就是没丢，大于 0 就要查丢弃原因。

批量插入的"尾巴滚动"问题
现象：压 N 条，数据库只入库 N-1 条左右。下次压，又多了 N-1 条。一多一少，反复滚动。

原因：批量攒批逻辑只在"攒够 BATCH_SIZE"或"超时 1 秒"时 flush。最后一批不满 BATCH_SIZE，会卡在 batch 里，等下次凑批。

解决方案：

加兜底：队列空了等 200ms 就 flush。
把 take() 改成 poll(100ms)。
注意：QUEUE_EMPTY_FLUSH_DELAY_MS 不能设为 0，否则低速生产时会频繁小批次 flush，批量优势降低。200ms 是经验值。

关键技术点
MQTT 是发布-订阅模型，与 HTTP 的请求-响应模型完全不同。

遗嘱消息：设备掉线时，Broker 自动发布离线通知。

性能优化：单线程 + 单条插入是性能杀手，必须异步化 + 批量插入 + 专用时序数据库。

本项目落地（截至 v1.4）
已落地：4 线程消费 + 1000 条/批（设计）+ Redis 幂等 + 死信。压测 50000 条，EMQX 丢弃 0。

已落地：MySQL + TDengine 双写，压测 5 万条两边数据一致（49802 = 49802），无死信。

下一步：冷热分离、查询切换（看板/趋势图走 TDengine）。

十九、时序库双写架构（TDengine）
解决什么问题
MySQL 是行存，为事务和随机读写设计。时序数据是"写多读少、按时间聚簇"，用关系型库存是错配。

高频写入时，MySQL 的 B+ 树索引维护开销大；按时间范围查询也慢。

工业场景需要"高频写入 + 时间范围聚合"，专用时序库是正解。

为什么选 TDengine
国产、单机性能强、自带 SQL 接口和超级表模型。

对 Java 团队上手成本低，JDBC 驱动和现有 SpringBoot 无缝集成。

超级表（一设备一子表）正好匹配工业设备模型。

架构设计
text
MQTT → 幂等 → 攒批 → flush
                       ├─ MySQL（主链路）
                       └─ TDengine（旁路）
MySQL 主链路：告警、事务依赖它，强一致。

TDengine 旁路：时序存储、后续查询，最终一致。

异常隔离：主链路失败整批进死信；旁路失败只记死信，不影响主链路。

连接方式
TDengine JDBC 有两种连接方式：

方式	URL 前缀	原理	需要什么
原生连接	jdbc:TAOS://	走 libtaos 动态库	本地装 TDengine 客户端
REST 连接	jdbc:TAOS-RS://	走 taosAdapter HTTP	端口通即可
本项目选 REST（jdbc:TAOS-RS://localhost:6041/smart_office），容器化场景不依赖本地库。

写入优化
逐设备写入：47 个设备 47 次网络往返，慢（296ms/批）。

多子表合并 INSERT：一条 SQL 写多个子表，一批只发一次。语法要点——只有第一个子表带 INSERT INTO，后续子表直接写子表名。

优化后：单批 8~10ms，反超 MySQL 的 13~30ms。

数据一致性保障
时间戳唯一：TDengine 主键 (ts, tag)，同一设备同一时间戳会覆盖。压测脚本用 baseTs + i 全局递增，保证唯一。

reportTime 显式设置：不依赖 MyBatis-Plus 自动填充（那只在 MySQL 插入时生效，内存对象可能为 null，导致 TDengine 写入 NPE 静默丢）。

死信记录：旁路失败进死信，message_id 用 topic + nanoTime 保证唯一。

二十、启动编排与依赖就绪
核心思想
项目启动 ≠ 依赖就绪。SpringBoot 启动过程中，MySQL、Redis、RabbitMQ、MQTT Broker、Nacos 各有各的就绪时间戳。只有所有依赖都返回就绪信号，项目才能对外提供服务。

各依赖的就绪时间戳
组件	就绪标志
MySQL	HikariPool-1 - Start completed.
Redis	连接池初始化成功
RabbitMQ	Created new connection
MQTT Broker	connectComplete 回调触发 + SUBACK 返回
Nacos	Located property source
TDengine	TDengine 连接建立成功
Tomcat	Tomcat started on port 8080
SpringBoot	Started SmartOfficeApplication
MQTT 订阅的就绪标志
错误做法：Thread.sleep(1000) 等订阅就绪。这是"时间赌注"，网络慢、Broker 忙，1 秒不够就丢消息。

正确做法：用 MqttCallbackExtended.connectComplete() 回调。连接真正建立后，在回调里订阅，订阅成功（SUBACK 返回）后，才认为 MQTT 就绪。

启动编排的实现方式
ApplicationRunner / CommandLineRunner：容器完全启动后执行。

@EventListener(ApplicationReadyEvent.class)：监听"应用就绪"事件。

SpringBoot Actuator 的 /actuator/health：生产环境用，配合 Kubernetes Readiness Probe。

二十一、Nacos 配置管理
解决什么问题
问题一：配置写死在 application.yml，改了要重新打包、重启服务。

问题二：多环境配置分散，改公共配置容易漏。

问题三：生产环境重启服务 = 短暂停机。

Nacos 是什么
Nacos 是配置中心 + 注册中心。本项目用它的配置中心能力。

核心能力：

配置集中管理：所有配置放 Nacos，不放 JAR 里

动态刷新：改 Nacos 配置，服务不重启就生效

多环境隔离：按 namespace 区分 dev / test / prod

版本管理：每次改配置有历史记录，可回滚

接入方案
本地 application.yml 只保留最小配置：

yaml
spring:
  application:
    name: smart-office
  profiles:
    active: docker
  cloud:
    nacos:
      config:
        server-addr: localhost:8848
        file-extension: yml
        namespace: public
        group: DEFAULT_GROUP
完整配置放 Nacos：

Data ID：smart-office-docker.yml

Group：DEFAULT_GROUP

格式：YAML

为什么 Data ID 是 smart-office-docker.yml：

SpringBoot 拼 Data ID 的规则：

text
${spring.application.name}-${spring.profiles.active}.${file-extension}
= smart-office-docker.yml
动态刷新机制
长轮询（Long Polling）：

text
SpringBoot 发请求给 Nacos："我订阅的配置，有变化吗？"
  ↓
Nacos 挂起请求（最多 30 秒）
  ↓
30 秒内有变化：Nacos 立刻返回"变了"
30 秒内没变化：Nacos 返回"没变"
  ↓
SpringBoot 收到响应，立刻再发一次
  ↓
循环
关键：Nacos 把请求挂起，有变化立刻推，没变化等超时再回。
好处：实时 + 省资源（不是每秒问一次）。

Bean 生命周期管理
@RefreshScope：配置变了，Bean 销毁重建。

@PreDestroy：Bean 销毁前，主动断开旧连接。

MqttConfig 完整写法：

java
@RefreshScope
@Configuration
public class MqttConfig {

    @Value("${mqtt.broker}")
    private String broker;

    @Value("${mqtt.client-id}")
    private String clientId;

    private MqttClient mqttClient;

    @Bean
    public MqttClient mqttClient() throws Exception {
        mqttClient = new MqttClient(broker, clientId, new MemoryPersistence());
        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(false);
        options.setAutomaticReconnect(true);
        mqttClient.connect(options);
        return mqttClient;
    }

    @PreDestroy
    public void destroy() {
        if (mqttClient != null && mqttClient.isConnected()) {
            mqttClient.disconnect();
            mqttClient.close();
        }
    }
}
动态刷新的边界
能动态刷新：开关类配置（告警阈值、限流开关、功能开关）。

不推荐动态刷新：连接类配置（MQTT 连接、数据库连接池）。

原因：重连有抖动，可能短时间收不到消息；两个 client 同时在线，可能重复消费。

正确做法：连接类配置改完，重启服务。

踩坑记录
见 BUG_LOG 问题 49-56。

二十二、监控体系（Prometheus + Grafana）
解决什么问题
服务出问题，不知道哪一层挂了。需要"可观测性"。

三层架构
text
SpringBoot（暴露指标）
  ↓
Prometheus（采集 + 存 + 告警）
  ↓
Grafana（可视化）
SpringBoot 暴露指标
依赖：micrometer-registry-prometheus + spring-boot-starter-actuator

端点：/actuator/prometheus

配置：

yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,metrics
  endpoint:
    health:
      show-details: always
  metrics:
    tags:
      application: smart-office
Prometheus 采集
抓取间隔：15 秒

抓取目标：http://smart-office:8080/actuator/prometheus（Docker 内网服务名）

告警规则：3 条（HTTP 5xx、JVM 内存、服务下线）

Grafana 可视化
数据源：http://smart-office-prometheus:9090

大盘：JVM、HTTP

常用模板：4701（JVM）、11378（SpringBoot）

关键设计
分层：暴露、采集、可视化，各司其职。

可替换：Grafana 换成 Kibana 也行。

告警闭环：Prometheus 检测 + 触发，Alertmanager 负责通知（可选）。

二十三、应用 vs 环境：本地开发与容器部署的区别
核心认知
SpringBoot 是"应用"，需要一堆"外部服务"（MySQL、Redis、EMQX、Nacos、TDengine）才能工作。这些不是 SpringBoot 的一部分，是"外部依赖"。应用启动时连不上它们，就报错。

两种跑法
维度	方式 A：全容器（部署）	方式 B：IDEA + Docker 中间件（开发）
SpringBoot 在哪	Docker 容器	本地 IDEA
中间件在哪	Docker 容器	Docker 容器
连中间件用什么地址	服务名（mysql:3306）	localhost + 映射端口（localhost:3307）
改代码生效	要重建镜像、重启容器	直接 Run 就生效
适合场景	部署、验证	开发、调试
为什么本地开发用 Docker 跑中间件
不污染系统：卸载容器 = 一切清空，无残留。

版本可控：docker-compose.yml 写死版本，团队一致。

一键起停：docker compose up -d / down，比手动开关服务快。

环境隔离：不同项目用不同容器，端口不冲突。

和部署一致：本地 Docker 起中间件，和生产用同一套 compose，减少"本地能跑、线上跑不了"。

关键约束
SpringBoot 实例只能有一个：本地 IDEA 和容器不能同时启用，否则抢 clientId、抢端口、抢资源。

Nacos 是启动依赖：bootstrap 阶段就要连，连不上直接启动失败。

中间件停了，应用连不上：docker compose stop <服务名> 只停单个；docker compose down 停全部。

配置分层
本地骨架：bootstrap.yml（Nacos 接入信息）+ application.yml（应用名、端口）。

Nacos 业务配置：smart-office-docker.yml，包含所有连接信息，运行时从 Nacos 拉取。

容器环境变量：docker-compose.yml 里的 environment，注入服务名。

配置模板用变量：jdbc:mysql://${MYSQL_HOST:localhost}:${MYSQL_PORT:3307}/... —— 本地默认 localhost，容器注入 mysql，一份模板适配多环境。