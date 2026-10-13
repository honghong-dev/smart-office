markdown
# Smart-Office 企业级智慧协同办公平台

基于 SpringBoot 3 + SpringCloud 2023 + MyBatis-Plus + MySQL + Redis + RabbitMQ + EMQX + Redisson + Nacos + TDengine + Prometheus + Grafana **+ DeepSeek API** 的企业级后端系统。

**多模块 Maven 工程**（`smart-office-app` 业务模块 + `smart-office-simulation` 设备仿真模块），已实现 Docker Compose 一键部署、MQTT 工业物联网数据链路（QoS 1 + 持久会话 + 补发 + 幂等 + 批量插入 + 死信）、审批流延迟队列、分布式锁、三级权限管理、审计日志、数据权限、Nacos 配置管理、Prometheus + Grafana 监控、MySQL + TDengine 时序库双写（数据一致性已验证）、**AI Agent 智能监控（规则引擎 + Function Calling + 告警去重）**。

---

## 技术栈

| 分类 | 技术 |
|------|------|
| 后端框架 | SpringBoot 3.3.5, Spring MVC, SpringCloud 2023.0.3 |
| 持久层 | MyBatis-Plus 3.5.5, MySQL 8.0 |
| 时序数据库 | TDengine 3.3（REST 连接，多子表合并 INSERT） |
| 缓存 | Redis |
| 消息队列 | RabbitMQ 3.13.7 + Erlang 26 |
| 分布式锁 | Redisson 3.27.2 |
| 安全 | JWT (jjwt 0.11.5), BCrypt (强度 12) |
| 权限模型 | RBAC（用户-角色-权限），三级管理，接口级鉴权，数据权限 |
| 配置中心 | Nacos 2.3.2 |
| 监控 | Prometheus + Grafana |
| 工业物联网 | EMQX (MQTT Broker), Eclipse Paho MQTT Client 1.2.5 |
| **AI 层** | **DeepSeek API, Function Calling, 规则引擎, LLM 客户端抽象** |
| 容器化 | Docker Compose + WSL 2 |
| 构建工具 | Maven 3.9+ |
| 版本管理 | Git + Gitee |

---

## 项目结构（多模块）

```text
smart-office (父 pom)
├── smart-office-app                业务模块
│   └── com.example.smart_office
│       ├── annotation              (RequiresPermission)
│       ├── common                  (Result, GlobalExceptionHandler)
│       ├── config                  (MqttConfig, RabbitMQConfig, MybatisPlusConfig, WebConfig, ...)
│       ├── controller              (UserController, ApprovalController)
│       ├── entity                  (SysUser, SysRole, DeviceTelemetry, dto/, ...)
│       │   └── ai                  (AiAnalysisLog, AlertRecord)          ★ v1.6 新增
│       ├── interceptor             (JwtInterceptor)
│       ├── mapper                  (XXXMapper)
│       │   └── ai                  (AiAnalysisLogMapper, AlertRecordMapper)  ★ v1.6 新增
│       ├── service
│       │   ├── ai                  (AiMonitorService, RuleEngine, LlmClient, MockLlmClient, AiToolExecutor)  ★ v1.6 新增
│       │   ├── MqttSubscriber
│       │   ├── TelemetryQueue
│       │   ├── TelemetryBatchWriter
│       │   └── TdengineWriteService
│       └── utils                   (JwtUtils)
│
├── smart-office-simulation         设备仿真模块（独立运行，不部署生产）
│   └── com.example.smart_office.simulation
│       ├── domain                  (Device, DeviceState, FaultType, FaultState, ProtectionAction, ...)
│       ├── engine                  (TelemetrySimulator, FaultEvolver, FaultInjector, ProtectionEngine, SimulationScheduler)
│       ├── infrastructure.mqtt     (MqttProperties, TelemetryPublisher)
│       ├── application             (FaultManagementService, CommandLineController)
│       └── config                  (DeviceSeeder, DeviceGenerationProperties)
│
└── deploy                          部署编排（Docker Compose + Prometheus + SQL 初始化）
已完成功能
用户与安全
用户注册（BCrypt 加密，强度 12，注册自动分配 USER 角色）

用户登录（JWT 无状态鉴权，严格校验 Bearer 前缀）

分页查询（Redis 缓存 + 防缓存穿透 + 数据权限）

统一返回格式（Result<T>）

全局异常处理（GlobalExceptionHandler）

权限与审批
RBAC 权限模型（用户-角色-权限，三张表 + 两张关联表）

三级管理：老板（SUPER_ADMIN）、厂长（ADMIN）、班长（SUB_ADMIN）、员工（USER），越级拦截

注册自动分配 USER 角色，新用户有基础权限

登录时权限写入 Redis（30 分钟过期）

自定义 @RequiresPermission 注解

JwtInterceptor 接口级权限校验（无权限返回 403）

权限缓存刷新（Redis Pub/Sub）：权限变更秒级生效

审批流提交接口（事务控制 + 审批记录 + MQ 异步通知）

审批流延迟队列：TTL + 死信队列，30 分钟超时提醒，消费端 Redis 幂等

分布式锁（Redisson）：审批处理并发控制

审计日志：记录权限变更、审批处理

数据权限：加部门表，用户表加 dept_id，非管理员只能看本部门

工业物联网（MQTT）
EMQX Broker + Eclipse Paho MQTT Client

订阅 factory/device/+/telemetry（统一遥测包）

持久会话（cleanSession=false + 固定 clientId + session_expiry_interval=7200）

离线补发：app 掉线期间 EMQX 缓存消息到 offline 队列，重连后自动补发（实测补发 3200 条，1 秒内全部入库）

EMQX max_mqueue_len=100000（离线队列容量调大）

Redis SETNX 幂等，QoS 1 重复投递不重复入库

线程池 + 批量插入（设计 1000 条/批，4 线程并行消费）

死信记录（解析失败、入库失败不丢弃）

双写一致性验证：100 台设备每秒 100 条遥测，MySQL 与 TDengine 数据 100% 一致

时序库双写（TDengine）
MySQL 主链路 + TDengine 旁路，异常隔离

主链路失败整批进死信；旁路失败只记死信，不影响主链路

TDengine REST 连接（jdbc:TAOS-RS://）

超表 + 子表架构：一设备一子表，CREATE TABLE ... USING telemetry TAGS(...)

app 启动自动建超表（CREATE STABLE IF NOT EXISTS telemetry ...）

多子表合并 INSERT（一条 SQL 写多个子表），单批 8~10ms

MySQL 加联合索引 idx_device_time (device_id, report_time)，EXPLAIN 验证走索引

★ AI Agent 智能监控（v1.6 新增）
规则引擎（RuleEngine）：4 条规则，覆盖工业设备核心异常维度

温度 > 95℃ → CRITICAL

温度 > 85℃ → WARNING

振动 > 10 mm/s → CRITICAL

状态 == FAULT → CRITICAL

短路返回 + 优先级（FAULT > 温度 > 振动 > 预警）

LLM 客户端抽象（LlmClient）：接口 + Mock 实现（MockLlmClient），可无缝替换为 DeepSeek

Function Calling 工具（AiToolExecutor）

query_device_history(deviceId, limit) —— 查设备历史遥测

send_alert(deviceId, level, reason) —— 写 MySQL + 发 RabbitMQ

主 Agent（AiMonitorService）：@Async 异步处理 + Redis SETNX 去重（5 分钟冷却）+ 防御性拷贝

挂载点：TelemetryBatchWriter.flush() → MySQL 入库后 → AI Agent（异步，不阻塞主链路）

数据表：ai_analysis_log（AI 分析日志）+ alert_record（告警记录）

MQ 拓扑：ai.alert.queue（含死信队列）

单元测试：5 个用例覆盖规则引擎所有分支（JUnit 5，全过，31ms）

数据对账：24 条 AI 分析 = 8 WARNING + 16 CRITICAL（8 TEMP_GT_95 + 8 STATE_FAULT），逐层可追溯

设备仿真（smart-office-simulation）
配置驱动：支持 10 / 100 / 1000 台设备

设备类型：冲压机、焊机、涂装设备、总装设备、传送带

设备状态机：RUNNING ⇄ IDLE → FAULT → MAINTENANCE（非法迁移抛异常）

物理模型：温度 = 基准 + 昼夜周期 + 劣化趋势 + 高斯噪声

故障演化引擎：4 类故障模式

轴承磨损（振动缓慢上升）

过热（温度指数上升）

传感器漂移（数值偏移）

突发断裂（前期正常，之后跳变）

保护域（工业硬逻辑，不走 AI）：

熔断：温度 > 95℃ 降载，> 105℃ 停机

停机：振动 > 10 mm/s 停机

复位：FAULT → MAINTENANCE → IDLE

手动注入：命令行 inject {deviceId} {faultType}

消息队列
RabbitMQ 异步解耦（注册后异步通知）

RabbitMQ 延迟队列（TTL + 死信队列，审批超时提醒）

AI 告警队列（含死信队列）

生产者 + 消费者完整链路

配置管理（Nacos）
Nacos 容器化部署

配置从本地 application.yml 迁移到 Nacos

多环境配置（dev / docker / prod）

@RefreshScope 动态刷新

@PreDestroy 管理 MQTT 连接生命周期

环境变量占位（本地用 localhost，Docker 用服务名）

监控体系（Prometheus + Grafana）
SpringBoot 通过 Micrometer 暴露 /actuator/prometheus 端点

Prometheus 采集指标，存时序数据

Grafana 可视化：JVM、HTTP、Redis 大盘

告警规则 3 条：

HTTP 5xx 错误率 > 1%，持续 1 分钟

JVM 堆内存 > 85%，持续 5 分钟

服务下线，持续 1 分钟

工程化
多模块 Maven 工程（父 pom + 业务模块 + 仿真模块，职责隔离）

Dockerfile 编写 + 镜像构建

Docker Compose 编排：一键启动 9 个服务

环境变量覆盖：MQTT_HOST、REDIS_HOST、TDENGINE_HOST、SPRING_CLOUD_NACOS_CONFIG_SERVER_ADDR 等

Git + Gitee 版本管理，.gitignore 排除 target/、data/、日志

完整的 9 份项目文档（新增 docs/AI_AGENT_DESIGN.md）

快速启动
1. 打包
bash
mvn clean package -DskipTests
2. 启动（Docker Compose）
bash
cd deploy
docker compose up -d
3. 看容器状态
bash
docker compose ps
9 个容器，都 Up：

smart-office-mysql

smart-office-redis

smart-office-rabbitmq

smart-office-emqx

smart-office-nacos

tdengine

smart-office-app（可选，开发时本地跑）

smart-office-prometheus

smart-office-grafana

4. 本地起 app + 仿真器
bash
# 终端 1：起 app
java -jar smart-office-app/target/smart-office-0.0.1-SNAPSHOT.jar

# 终端 2：起仿真器（100 台）
java -jar smart-office-simulation/target/smart-office-simulation-0.0.1-SNAPSHOT.jar
5. 访问各服务
服务	地址	账号密码
SpringBoot	http://localhost:8081	-
Nacos	http://localhost:8848/nacos	nacos / nacos
Prometheus	http://localhost:9090	-
Grafana	http://localhost:3000	admin / admin123
RabbitMQ	http://localhost:15673	admin / 123456
EMQX Dashboard	http://localhost:18084	admin / public
仿真器命令
启动 smart-office-simulation 后，控制台支持：

text
inject {deviceId} {faultType}   注入故障
    faultType: BEARING_WEAR / OVERHEAT / SENSOR_DRIFT / SUDDEN_FAILURE
reset {deviceId}                 复位
random {probability}             随机批量注入
status                           查看所有设备状态
help                             帮助
quit                             退出
示例：

text
inject PRESS-01-01 OVERHEAT
下一步计划
第一阶段：L1 收尾
顺序	项目	优先级	状态
1	Nacos 配置管理	P0	✅
2	监控体系 Prometheus + Grafana	P0	✅
3	TDengine 双写	P0	✅
4	MQTT 持久会话 + 补发	P0	✅
5	MySQL 索引 + P0 优化	P0	✅
6	AI Agent MVP（Mock 版）	P0	✅ v1.6
7	接 DeepSeek 真 API	P0	⬜
8	时序库冷热分离	P0	⬜
9	WebSocket 站内信推送	P1	⬜
10	仿真层班次调度 + 产线故障传导	P1	⬜
第二阶段：L2 微服务
顺序	项目	优先级	工作量
11	微服务拆分：Nacos + Gateway + OpenFeign	P1	3-4 周
12	链路追踪：SkyWalking	P2	1 周
第三阶段：L3 差异化（核心目标）
顺序	项目	优先级	工作量
13	AI Agent RAG 文档问答	P0	1 周
14	Agent 多轮工具编排	P0	1 周
15	告警 MQ 消费者（钉钉/邮件）	P1	3 天
16	评估体系：精确率/召回率	P1	3 天
分级说明
L0 能跑：功能通，单人能用

L1 能扛：并发 + 容错 + 可观测 + 配置管理

L2 能扩：微服务 + 网关 + 配置中心

L3 能演：可观测 + 可回滚 + 有 SLA

详细文档
docs/API.md：接口文档

docs/DATABASE.md：数据库设计

docs/DEPLOYMENT.md：部署与运维手册

docs/ENVIRONMENT.md：开发环境清单

docs/BUG_LOG.md：踩坑日志

docs/PROGRESS.md：项目进度与架构蓝图

docs/ARCHITECTURE.md：架构与原理笔记

docs/RBAC_DESIGN.md：RBAC 权限模型设计

docs/AI_AGENT_DESIGN.md：AI Agent 设计文档（v1.6 新增）