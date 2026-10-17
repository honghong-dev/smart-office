# Smart-Office 架构与原理笔记

文档版本：v1.8
最后更新：2026-10-17
对应代码版本：Smart-Office v1.8

本文档记录项目开发、部署、工业物联网接入、配置管理、监控体系过程中涉及的核心原理与底层逻辑，是"为什么这么做"的答案。

## 变更记录

| 版本 | 日期 | 变更内容 |
|------|------|----------|
| v1.0 | 2026-09-27 | 请求链路、端口映射、容器网络 |
| v1.1 | 2026-09-29 | RBAC、审批流、分布式锁 |
| v1.2 | 2026-10-04 | Redis Pub/Sub、审计日志、数据权限 |
| v1.3 | 2026-10-05 | Nacos 配置管理、监控体系 |
| v1.4 | 2026-10-05 | TDengine 双写架构 |
| v1.5 | 2026-10-09 | 多模块工程；MQTT 持久会话 + 补发；设备仿真层；双写修复；索引优化 |
| v1.6 | 2026-10-13 | AI Agent 架构（规则引擎 + LLM 双层） |
| v1.7 | 2026-10-15 | AI Agent 接入 DeepSeek 真 LLM；Mock↔DeepSeek 配置切换 |
| v1.8 | 2026-10-17 | 冷热分离查询路由架构；WebSocket STOMP 实时推送架构 |

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
MySQL 8.0 默认使用 caching_sha2_password。旧版 JDBC 驱动不认识这种新加密方式，导致连接被拒。
解决方案：ALTER USER 'root'@'%' IDENTIFIED WITH mysql_native_password BY '密码';
（这只影响"连接协议"，不影响业务层面的 BCrypt 密码加密）。

五、RabbitMQ 权限模型
用户必须被授予 vhost 的访问权限，否则报 NOT_ALLOWED。
在管理后台 Admin → Users 中点击 Set permission，填入 .* 即可。

六、防火墙与安全
本地开发可临时关闭防火墙，但不能长期裸奔。正确做法：开放白名单（如 Docker 网段 172.31.0.0/16）。

生产环境：数据库/Redis/RabbitMQ 端口绝对不开放公网，只允许内网访问。

密码必须复杂：长度 ≥16，大小写 + 数字 + 特殊符号。

应用连接数据库使用最小权限用户，不使用 root。

七、本地访问 vs 公网访问
本地：localhost 或局域网 IP。

公网：需要租云服务器 + 域名 + Nginx 反向代理 + HTTPS 证书。Nginx 负责转发请求到 8080，对外只暴露 80/443。

八、Docker Compose 一键部署核心思想
用 docker-compose.yml 编排所有服务，一条命令 docker compose up -d，一键启动。

服务清单：

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
九、RBAC 权限模型
详细设计见 RBAC_DESIGN.md，此处只记录核心原理。

模型：sys_user → sys_user_role → sys_role → sys_role_permission → sys_permission

缓存设计：登录后权限写入 Redis（30 分钟过期），变更时 Pub/Sub 清缓存。

十、审批流设计
详细设计见 PROGRESS.md。

延迟队列：TTL = 30 分钟。超时进死信队列触发消费。

分布式锁：Redisson，approval:lock:{approvalId}，双保险（锁 + 状态校验）。

事务一致性：使用 TransactionSynchronizationManager.afterCommit() 保证"事务提交后才发消息"。

十一、审计日志与数据权限
审计日志：独立 AuditLogService，try-catch 包裹，失败不影响主流程。

数据权限：加 sys_dept 表，查询时非管理员自动追加 WHERE dept_id = 当前部门。cacheKey 必须带 userId 避免串数据。

十二、工业物联网数据链路（MQTT）
QoS 选型：QoS 1 + 应用层幂等（效果上等于 QoS 2，吞吐量高得多）。

持久会话：cleanSession=false。Broker 保留订阅关系和离线消息，重连自动补发。

回调原则：messageArrived 里只做入队（TelemetryQueue.offer），不能做 IO（日志/Redis），防止阻塞导致 ACK 推不出去。

批量插入：4 线程攒批（1000 条/批）。解决"尾巴滚动"问题（队列空了等 200ms 就 flush）。

丢弃原因：无订阅者、离线队列满（max_mqueue_len 超限）、飞行窗口满（max_inflight）。

十三、时序库双写架构（TDengine）
为什么选 TDengine：国产、单机性能强、自带超级表模型。

连接方式：REST 连接（jdbc:TAOS-RS://），容器化场景不依赖本地库。

超表 + 子表：子表依赖超表存在（USING telemetry TAGS）。

写入优化：

先 ensureSubTables() 预建子表（防大批量首次写入失败）。
多子表合并 INSERT（只有第一个带 INSERT INTO）。
单批 8~10ms，反超 MySQL。
一致性保障：显式设置 reportTime，不依赖 MyBatis-Plus 自动填充（防止内存对象为 null，写入静默丢失）。

十四、设备仿真层（smart-office-simulation）
定位：独立模块，通过 MQTT 发数据。生产不部署。

分层：domain / engine / infrastructure / application / config。

状态机：RUNNING ⇄ IDLE → FAULT → MAINTENANCE，非法迁移抛异常。

保护域（工业硬逻辑）：温度 >95℃ 降载，>105℃ 停机；振动 >10mm/s 停机。不走 AI，去重防失控。

十五、启动编排与依赖就绪
核心思想：项目启动 ≠ 依赖就绪。

MQTT 订阅就绪：不能用 Thread.sleep，必须用 connectComplete() 回调 + SUBACK 返回。

实现方式：ApplicationReadyEvent 或 CommandLineRunner。

十六、Nacos 配置管理
长轮询：Nacos 挂起请求（最多 30 秒），有变化立刻推，没变化等超时再回。实时且省资源。

Bean 生命周期：@RefreshScope 销毁重建，@PreDestroy 断开旧连接（MQTT 必须配）。

边界：开关类可动态刷新；连接类（MQTT/数据库池）改完必须重启服务。

十七、监控体系（Prometheus + Grafana）
三层架构：SpringBoot 暴露指标（Micrometer） → Prometheus 采集存储 → Grafana 可视化。

告警规则：HTTP 5xx、JVM 内存、服务下线。

十八、多模块工程结构
父 pom 只管版本管理，业务依赖放子 pom。
仿真模块只依赖基础组件（spring-boot-starter + jackson + paho），不引入 web/mysql/redis（依赖隔离）。
显式声明 project.build.sourceEncoding=UTF-8，防 Windows GBK 乱码。

十九、AI Agent 架构（v1.6 - v1.7）
19.1 核心洞察
LLM 不是"看门狗"，是"研判员"。看门狗是规则引擎。

第一层（毫秒级）：规则引擎过滤 99% 正常数据。

第二层（秒级）：LLM 对异常片段深度研判。

19.2 为什么不全量喂 LLM
成本爆炸（每秒 100 次）、延迟爆炸（2-5 秒响应）、幻觉风险（正常数据也报异常）。

19.3 为什么挂载在 flush() 之后
挂载在 messageArrived 会阻塞 ACK 导致重发。

挂载在 take() 后数据没入库。

挂载在 flush() 后：数据已入库可查历史 + 异步不阻塞主链路。

19.4 核心设计点
Redis SETNX 去重：ai:agent:dedup:{deviceId}:{ruleName}，5 分钟 TTL，防告警风暴。

@Async 异步 + 防御性拷贝：new ArrayList<>(batch)，遍历副本防 ConcurrentModificationException。

LLM 客户端抽象：LlmClient 接口 + Mock / DeepSeek 实现，配置切换，依赖倒置。

原生 HTTP 接入：不用 Spring AI，用 RestClient 拼 HTTP，面试能讲透底层原理。

二十、冷热分离查询架构（v1.8 新增）
20.1 为什么做冷热分离
MySQL：行存，适合事务和随机读写。高频查询时间范围性能差。

TDengine：时序库，按时间分片。适合高频写入与近期数据查询。

方案：近期（7 天）查 TDengine，历史查 MySQL。

20.2 路由实现
核心类：DeviceTelemetryQueryService。

解析 startTime 和 endTime，计算边界 now().minusDays(7)。

全热：直接走 TDengine。

全冷：直接走 MySQL。

跨边界：两边分别查，合并后按时间排序返回。

对前端透明：前端只发一次请求，后端内部自动路由。

二十一、WebSocket 站内信架构（v1.8 新增）
21.1 为什么做 WebSocket
传统站内信靠刷新页面查库，实时性差。工业告警需要秒级推送。

21.2 技术选型
Spring WebSocket + STOMP 协议。

STOMP 提供类似消息队列的订阅发布语义，前端集成简单。

21.3 架构核心
配置：@EnableWebSocketMessageBroker，端点 /ws，允许跨域 setAllowedOriginPatterns("*")。

拦截器放行：JwtInterceptor 必须排除 /ws/**，否则 WebSocket 握手会被当成 HTTP 请求被 401 拦截。

消息代理：简单内存代理，/topic 广播（大屏告警），/queue/user/{id} 点对点（审批超时）。

推送逻辑：NotificationService 封装 SimpMessagingTemplate.convertAndSend()。业务层调用此 Service 实现推送。
```