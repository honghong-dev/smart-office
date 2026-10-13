markdown
# Smart-Office 项目进度与架构蓝图

**文档版本**：v1.6
**最后更新**：2026-10-13
**对应代码版本**：Smart-Office v1.6
本文档记录 Smart-Office 项目从零开发到多模块、容器化、工业物联网、仿真层、数据一致性的完整进度。

## 变更记录

| 版本 | 日期 | 变更内容 |
|------|------|----------|
| v1.0 | 2026-09-27 | 基础框架、JWT、RBAC、MQTT |
| v1.2 | 2026-10-04 | 审批流、延迟队列、审计日志、数据权限 |
| v1.3 | 2026-10-05 | Nacos、Prometheus、Grafana |
| v1.4 | 2026-10-05 | TDengine 双写 |
| v1.5 | 2026-10-09 | 多模块重构、设备仿真层、MQTT 持久会话 + 补发、数据零丢失验证、文档全面更新 |
| **v1.6** | **2026-10-13** | **AI Agent 智能监控（规则引擎 + Function Calling + 告警去重）；单元测试 + 数据对账；9 份文档更新到 v1.6** |

---

## 一、项目定位

- **项目名称**：企业级智慧协同办公平台（Smart-Office）
- **技术栈**：SpringBoot 3.3.5 + SpringCloud 2023 + MyBatis-Plus 3.5.5 + MySQL 8.0 + Redis + JWT + RabbitMQ 3.13.7 + Redisson 3.27.2 + Docker + Docker Compose + EMQX + Paho MQTT 1.2.5 + Nacos 2.3.2 + TDengine 3.3 + Prometheus + Grafana + Git/Gitee
- **目标**：打造一个具备高并发、高可用、可扩展的工业级后端系统，作为通往工业物联网方向的实战跳板。
- **设计来源**：按长城汽车冲焊车间真实工业场景设计，实现"工业物联网数据采集 + 三级权限管理 + 时序数据存储 + 设备仿真"完整闭环。

---

## 二、最新项目结构（多模块）

```text
smart-office (父 pom, packaging=pom)
├── smart-office-app                        业务模块
│   └── com.example.smart_office
│       ├── annotation (RequiresPermission)
│       ├── common (Result, GlobalExceptionHandler)
│       ├── config (WebConfig, MybatisPlusConfig, RabbitMQConfig, MqttConfig, MyMetaObjectHandler, PermissionCacheRefreshListener)
│       ├── controller (UserController, ApprovalController)
│       ├── entity (SysUser, SysRole, SysPermission, SysUserRole, SysApproval, SysApprovalRecord, DeviceTelemetry, SysAuditLog, SysDept, dto/)
│       │   └── ai (AiAnalysisLog, AlertRecord)          ★ v1.6 新增
│       ├── interceptor (JwtInterceptor)
│       ├── mapper (XXXMapper)
│       │   └── ai (AiAnalysisLogMapper, AlertRecordMapper)   ★ v1.6 新增
│       ├── service (SysUserService, SysApprovalService, AuditLogService, MqttSubscriber, TelemetryQueue, TelemetryBatchWriter, MqttIdempotentService, DeadLetterService, TdengineWriteService, impl/)
│       │   └── ai (AiMonitorService, RuleEngine, LlmClient, MockLlmClient, AiToolExecutor)  ★ v1.6 新增
│       └── utils (JwtUtils)
│
├── smart-office-simulation                 仿真模块（独立部署）
│   └── com.example.smart_office.simulation
│       ├── SimulationApplication
│       ├── domain (Device, DeviceState, DeviceType, DeviceStateMachine, DeviceRegistry, FaultType, FaultSeverity, FaultState, ProtectionType, ProtectionAction)
│       ├── engine (TelemetrySimulator, FaultEvolver, FaultInjector, ProtectionEngine, SimulationScheduler, TelemetrySample)
│       ├── infrastructure.mqtt (MqttProperties, TelemetryPublisher)
│       ├── application (FaultManagementService, CommandLineController)
│       └── config (DeviceSeeder, DeviceGenerationProperties)
│
└── deploy                                   部署编排
    ├── docker-compose.yml
    ├── data/tdengine/
    ├── prometheus/
    ├── sql/
    └── ...
resources（smart-office-app）：

application.yml（本地骨架配置：Nacos 地址 + 应用名 + 环境）

bootstrap.yml（SpringCloud Bootstrap 配置）

⚠️ v1.5 已删 .bak 备份文件（防误打包）

三、已完成模块
模块	功能	状态
环境搭建	JDK 17, MySQL, Redis, RabbitMQ, Erlang, WSL 2, Docker, EMQX, TDengine	✅
用户模块	注册（BCrypt，自动分配 USER 角色）、登录（JWT）、分页查询	✅
统一返回	Result<T> + GlobalExceptionHandler	✅
Redis 缓存	分页缓存 + 防缓存穿透 + 数据权限	✅
RabbitMQ	注册后异步发送通知（生产者 + 消费者）	✅
容器化	Dockerfile + Docker 镜像构建 + 容器运行	✅
Docker Compose	一键启动 9 个服务	✅
RBAC 权限	权限表 + 权限查询 + 权限缓存 + 接口级鉴权 + 三级管理	✅
审批流	提交审批 + 延迟队列（TTL + 死信）+ 分布式锁（Redisson）	✅
MQTT 设备接入	EMQX Broker + QoS 1 + 持久会话 + Redis 幂等 + 线程池 + 批量插入 + 死信	✅ 工业级
TDengine 双写	MySQL 主链路 + TDengine 旁路，异常隔离，多子表合并 INSERT	✅
权限缓存刷新	Redis Pub/Sub，权限变更秒级生效	✅
审计日志	记录权限变更、审批处理	✅
数据权限	部门隔离，非管理员只能看本部门	✅
Nacos 配置管理	多环境配置 + 动态刷新 + @RefreshScope + @PreDestroy	✅
监控体系	Prometheus + Grafana + 告警规则	✅
多模块 Maven	父 pom + app + simulation	✅ v1.5 新增
设备仿真层	10/100/1000 台设备，物理模型 + 故障演化 + 保护域	✅ v1.5 新增
MQTT 持久会话 + 补发	cleanSession=false + offline 队列 + 补发	✅ v1.5 新增
数据零丢失验证	app 掉线 30 秒，3200 条补发	✅ v1.5 新增
MySQL 索引优化	idx_device_time + EXPLAIN 验证	✅ v1.5 新增
文档	API/DB/DEPLOY/ENV/BUG_LOG/PROGRESS/ARCHITECTURE/RBAC_DESIGN/README	✅
Git 版本管理	Gitee 仓库：git@gitee.com:xiao-hongh/smart-office.git	✅ v1.5 新增
| **AI Agent 智能监控** | **规则引擎（4 条）+ Function Calling（2 个工具）+ LLM 客户端抽象 + Redis 去重** | **✅ v1.6 新增** |
| **AI Agent 单元测试** | **com.example.smart_office.service.ai.RuleEngineTest，5 个用例覆盖规则全分支（31ms 全过）** | **✅ v1.6 新增** |
| **数据一致性对账** | **24 条 AI 分析 = 8 WARNING + 16 CRITICAL，逐层可追溯** | **✅ v1.6 新增** |
| **AI 告警 MQ 拓扑** | **ai.alert.exchange + ai.alert.queue（含死信）** | **✅ v1.6 新增** |


四、RBAC 权限模块（已完成，含三级管理 + 数据权限）
4.1 基础 RBAC
☑ 创建 sys_permission 和 sys_role_permission 表

☑ 编写 SysPermission 实体类、Mapper

☑ 实现根据 userId 查询权限编码列表的接口

☑ 登录时权限写入 Redis（30分钟过期）

☑ 自定义 @RequiresPermission 注解

☑ JwtInterceptor 增加权限校验逻辑

☑ 验证"有权限 -> 200" 和 "无权限 -> 403" 两组场景

4.2 三级管理（老板、厂长、班长、员工）
☑ 四种角色：SUPER_ADMIN、ADMIN、SUB_ADMIN、USER

☑ 注册时自动分配 USER 角色

☑ assignRole 接口加三级校验

☑ 老板不能改自己，厂长能改自己，班长不能改自己

☑ 不能越级：班长不能改厂长，班长不能改同级班长

☑ 不能造第二个老板

☑ 测试通过

4.3 权限缓存刷新（Redis Pub/Sub）
☑ 改权限后，发消息到 permission:refresh 频道

☑ 各 Java 实例订阅频道，收到消息后清缓存

☑ JwtInterceptor 缓存未命中时，查库重建

☑ 测试通过：权限变更秒级生效

4.4 数据权限（部门隔离）
☑ 创建 sys_dept 表，sys_user 表加 dept_id

☑ assignDept 接口，分配部门

☑ getUserPage 加数据权限：非管理员，加 WHERE dept_id = 当前用户的部门

☑ cacheKey 加 userId，避免缓存串数据

☑ 测试通过

五、审批流模块（已完成，含延迟队列 + 分布式锁）
5.1 基础审批流
☑ 创建 sys_approval 和 sys_approval_record 表

☑ 编写 SysApproval、SysApprovalRecord 实体类

☑ 实现 submitApproval 接口（事务控制 + 审批记录 + MQ 异步通知）

☑ 权限控制：@RequiresPermission("approval:submit")

5.2 延迟队列（TTL + 死信队列）
☑ 创建 approval.delay.queue（TTL = 30 分钟）和 approval.dlx.queue

☑ 提交审批时，发延迟消息到延迟队列

☑ 30 分钟后，消息进死信队列，消费者查审批单状态

☑ 未批，发提醒；已批，跳过

☑ 消费端 Redis SETNX 幂等

5.3 分布式锁（Redisson）
☑ 引入 Redisson 依赖

☑ handleApproval 接口加锁：approval:lock:{approvalId}

☑ 拿到锁后，查审批单状态，已处理则跳过

☑ 测试通过：并发控制 + 状态校验双保险

六、MQTT 设备接入（已完成，工业级）
6.1 基础链路
☑ Docker 启动 EMQX Broker

☑ 订阅 factory/device/+/telemetry

☑ 写入 device_telemetry 表

6.2 工业级改造
☑ 去掉 Thread.sleep(1000)，改用 MqttCallbackExtended.connectComplete() 回调订阅

☑ QoS 1 + 持久会话（cleanSession=false + 固定 clientId + session_expiry_interval=7200）

☑ MQTT 回调只入队，不打日志（避免阻塞回调线程）

☑ Redis SETNX 幂等（key：mqtt:idempotent:{deviceId}:{timestamp}）

☑ 线程池 + 批量插入（4 线程，1000 条/批，1 秒超时兜底）

☑ 队列空了就 flush（防批量尾巴滚动）

☑ 死信记录（解析失败、入库失败不丢弃）

☑ report_time 自动填充（MyMetaObjectHandler）

☑ device_telemetry 加索引（idx_device_time）

☑ EMQX max_mqueue_len=100000（离线队列调大，防溢出）

6.3 压测数据
指标	数值
设备数	100 台
速率	100 条/秒
MQTT 持久会话	clean_start=false、session_expiry_interval=7200
双写一致性	MySQL 与 TDengine 数据 100% 一致
死信	0
6.4 数据零丢失验证（v1.5）
场景：app 掉线 30 秒后重连。

检查点	结果
app 掉线时 enqueued_msgs	3200（离线队列积压）
app 重连后 enqueued_msgs	0（补发完成）
MySQL 主表	3262
TDengine 超表	3166
补发入库耗时	< 1 秒
七、时序库双写（已完成，v1.4 起）
7.1 架构设计
☑ MySQL 主链路 + TDengine 旁路，异常隔离

☑ 主链路失败整批进死信；旁路失败只记死信，不影响主链路

☑ REST 连接（jdbc:TAOS-RS://），不依赖本地客户端

7.2 写入优化（v1.5 改进）
☑ 按设备分组，多子表合并 INSERT（一条 SQL 写多个子表）

☑ 先显式建子表（ensureSubTables() + 缓存），再纯 INSERT

☑ 单批 8~10ms，反超 MySQL 的 10~30ms

☑ app 启动自动建超表（CREATE STABLE IF NOT EXISTS）

7.3 数据一致性保障
☑ 时间戳唯一（压测脚本用 baseTs + i 全局递增）

☑ reportTime 显式设置（不依赖 MyBatis-Plus 自动填充）

☑ 死信 message_id 用 topic + nanoTime，保证唯一

☑ DeadLetterService.record 内部 catch 所有异常，不抛

7.4 压测数据
指标	数值
设备数	100 台
速率	100 条/秒
MySQL 入库	与 TDengine 一致
TDengine 单批耗时	8~10ms
两边一致性	✅ 完全一致
死信	0
八、设备仿真层（v1.5 新增）
8.1 模块定位
独立的 Maven 模块 smart-office-simulation，模拟 10/100/1000 台工业设备，通过 MQTT 发送遥测。生产环境不部署。

8.2 核心功能
☑ 配置驱动：line-count × types × count-per-type = 设备总数

☑ 设备类型：冲压机、焊机、涂装、总装、传送带

☑ 设备状态机：RUNNING ⇄ IDLE → FAULT → MAINTENANCE，非法迁移抛异常

☑ 物理模型：温度 = 基准 + 昼夜周期 + 劣化趋势 + 高斯噪声

☑ 故障演化引擎：4 类故障（轴承磨损/过热/传感器漂移/突发断裂）

☑ 保护域（工业硬逻辑）：温度 > 95℃ 降载，> 105℃ 停机；振动 > 10mm/s 停机

☑ 手动注入：命令行 inject {deviceId} {faultType}

☑ 故障注入自动切 RUNNING（故障只在运行中出现）

8.3 验证
10 台故障设备，4 种故障类型全触发保护

保护动作去重（loadReduced 标志）+ FAULT 状态温度冻结

仿真器独立运行，通过 MQTT 与主系统零代码耦合

九、审计日志（已完成）
☑ 创建 sys_audit_log 表

☑ 编写 SysAuditLog 实体、SysAuditLogMapper

☑ 编写 AuditLogService，记录：操作人、操作类型、目标、旧值、新值、IP、时间

☑ assignRole、handleApproval 加审计日志

☑ 测试通过

十、Nacos 配置管理（已完成）
☑ Nacos 容器化部署

☑ Nacos 连 MySQL，配置持久化

☑ 配置从本地 application.yml 迁移到 Nacos

☑ 多环境配置（dev / docker / prod）

☑ 环境变量占位（本地用 localhost，Docker 用服务名）

☑ @RefreshScope 动态刷新

☑ @PreDestroy 管理 MQTT 连接生命周期

☑ 踩坑 8 条（BUG_LOG 问题 49-56）

十一、监控体系（已完成）
☑ SpringBoot 通过 Micrometer 暴露 /actuator/prometheus 端点

☑ Prometheus 采集指标，存时序数据

☑ Grafana 可视化：JVM、HTTP 大盘

☑ 告警规则 3 条：

HTTP 5xx 错误率 > 1%，持续 1 分钟

JVM 堆内存 > 85%，持续 5 分钟

服务下线，持续 1 分钟

## 十一点五、AI Agent 智能监控（v1.6 新增）

### 11.5.1 模块定位

在 MQTT 数据链路之上，增加"AI 二次研判"层。用规则引擎前置过滤，LLM 对异常片段深度分析，通过 Function Calling 调工具（查历史、发告警）。

**核心洞察**：LLM 不是"看门狗"，是"研判员"。看门狗是规则引擎。

### 11.5.2 核心功能

**规则引擎（RuleEngine）**：
- ☑ 4 条规则：`STATE_FAULT`、`TEMP_GT_95`、`VIB_GT_10`、`TEMP_GT_85`
- ☑ 短路返回（命中第一个就返回）
- ☑ 优先级：FAULT > 温度 > 振动 > 预警

**LLM 客户端抽象（LlmClient）**：
- ☑ 接口 + Mock 实现（`MockLlmClient`）
- ☑ 配置驱动切换（`ai.llm.provider=mock/deepseek`）

**Function Calling 工具（AiToolExecutor）**：
- ☑ `query_device_history(deviceId, limit)`：查设备历史
- ☑ `send_alert(deviceId, level, reason)`：写 MySQL + 发 MQ

**主 Agent（AiMonitorService）**：
- ☑ `@Async` 异步执行（不阻塞主链路）
- ☑ 防御性拷贝（防 `ConcurrentModificationException`）
- ☑ Redis SETNX 去重（5 分钟 TTL，防告警风暴）

**挂载点**：
- ☑ `TelemetryBatchWriter.flush()` → MySQL 入库后 → AI Agent（异步）

**数据表**：
- ☑ `ai_analysis_log`：AI 分析日志
- ☑ `alert_record`：告警记录

**MQ 拓扑**：
- ☑ `ai.alert.queue`（含死信队列）

**测试**：
- ☑ 单元测试（`com.example.smart_office.service.ai.RuleEngineTest`，5 个用例，31ms 全过）
- ☑ 端到端测试（仿真层注入故障）
- ☑ 数据一致性对账（24 = 8 WARNING + 16 CRITICAL）

### 11.5.3 关键设计决策

| 决策 | 理由 |
|------|------|
| **规则引擎 + LLM 双层** | 全量喂 LLM → 成本爆炸 + 延迟爆炸 + 幻觉风险 |
| **挂载在 flush() 之后** | 数据已入库，LLM 查得到历史 |
| **@Async 异步** | 不阻塞主链路（MQTT → MySQL）|
| **Redis SETNX 去重** | 防告警风暴，5 分钟 TTL |
| **LLM 客户端抽象** | 开发用 Mock（不花钱），生产用 DeepSeek |

### 11.5.4 验证数据

| 指标 | 结果 |
|------|------|
| 单元测试 | 5 个用例，31ms 全过 |
| 端到端测试 | 注入 10 个故障，AI Agent 处理 8 个 |
| 数据对账 | 24 条 AI 分析 = 8 WARNING + 16 CRITICAL |
| 告警数 | 16 条（8 TEMP_GT_95 + 8 STATE_FAULT）|

**说明**：2 个故障未处理（`CONVEYOR-01-01` 振动缓慢上升、`ASSEMBLY-01-01` 瞬时故障），暴露了规则引擎覆盖缺口，是 v1.7 的优化方向。

### 11.5.5 详细设计

见 `docs/AI_AGENT_DESIGN.md`。

十二、待开发模块
模块	核心功能	优先级
| AI Agent 扩展 | RAG 文档问答 + 多轮工具编排 + 告警 MQ 消费者 | ⭐⭐⭐ v1.7 |
| DeepSeek 真 API | 替换 MockLlmClient | ⭐⭐⭐ v1.7 |
| 规则引擎补全 | 振动早预警（VIB_GT_5）+ 趋势异常检测 | ⭐⭐ v1.7 |
| 评估体系 | 仿真埋真值 → Agent 精确率/召回率 | ⭐⭐ |
评估体系	仿真埋真值 → Agent 精确率/召回率	⭐⭐
时序库查询切换	冷热分离、看板/趋势查询走 TDengine	⭐⭐
Alertmanager 告警通知	对接邮件/钉钉	⭐⭐
WebSocket 站内信推送	实时通知	⭐⭐
仿真层班次调度	设备自主进入 RUNNING/IDLE	⭐⭐
仿真层产线故障传导	上游故障 → 下游待料	⭐⭐
微服务拆分	Nacos + Gateway + OpenFeign	⭐
链路追踪	SkyWalking	⭐
十三、里程碑记录
2026-09-27：完成 JWT 鉴权、Redis 缓存、RabbitMQ 异步解耦、1000 并发压测。

2026-09-28：完成 Docker 容器化部署。

2026-09-29：完成 RBAC 权限模型、审批流提交接口、MQTT 设备接入。

2026-09-30：完成 MQTT 链路工业级改造。QoS 1 + 持久会话 + Redis 幂等 + 4 线程批量插入 + 死信记录。50000 条压测，EMQX 消息丢弃 0。

2026-10-01：完成审批流延迟队列、分布式锁、权限缓存刷新、三级管理。

2026-10-04：完成 Docker Compose 一键部署、审计日志、数据权限、Nacos 配置管理。

2026-10-05：完成监控体系、TDengine 时序库双写。压测 5 万条，两边数据一致（49802 = 49802）。

2026-10-09（Day 2，v1.5）：

✅ 单模块 → 多模块 Maven 工程重构（父 pom + app + simulation）

✅ 部署文件归位（deploy/ 目录）

✅ Nacos 配置重建 + 修复编码问题

✅ 代码 Lombok 化重构

✅ 仿真层（10/100/1000 台设备配置驱动 + 物理模型）

✅ 故障演化（4 类故障）+ 保护域（熔断/降载/停机/复位）

✅ MQTT 持久会话（cleanSession=false）+ offline 队列补发

✅ 数据零丢失验证（app 掉线 30 秒，3200 条补发，< 1 秒入库）

✅ TDengine 自动建超表（清库后不用手动建）

✅ EMQX max_mqueue_len=100000

✅ MySQL 索引 idx_device_time（EXPLAIN 验证走索引）

✅ .gitignore 补全

✅ Git 版本管理（Gitee 仓库：git@gitee.com:xiao-hongh/smart-office.git）

✅ 9 份文档全面更新至 v1.5
2026-10-13（Day 3，v1.6）：
- ✅ AI Agent 智能监控（规则引擎 + Function Calling + 工具执行 + 告警去重）
- ✅ 规则引擎（4 条规则 + 优先级 + 短路返回）
- ✅ LLM 客户端抽象（LlmClient 接口 + MockLlmClient）
- ✅ Function Calling 工具（query_device_history / send_alert）
- ✅ AiMonitorService（@Async + 防御性拷贝 + Redis SETNX 去重）
- ✅ 数据表 ai_analysis_log + alert_record
- ✅ MQ 拓扑 ai.alert.queue（含死信）
- ✅ 单元测试 com.example.smart_office.service.ai.RuleEngineTest（5 个用例，31ms 全过）
- ✅ 数据一致性对账（24 = 8 WARNING + 16 CRITICAL）
- ✅ BUG_LOG 新增 8 条（问题 77-84）
- ✅ 9 份文档全面更新至 v1.6
- ✅ 新建 AI_AGENT_DESIGN.md（10 章，含面试话术汇总）

十四、下一步计划
第一阶段：L1 收尾（目标：1 个月）
顺序	项目	优先级	状态
1	Nacos 配置管理	P0	✅
2	监控体系 Prometheus + Grafana	P0	✅
3	TDengine 双写	P0	✅
4	MQTT 持久会话 + 补发	P0	✅
5	MySQL 索引 + P0 优化	P0	✅
6	时序库查询切换（冷热分离）	P0	⬜
7	WebSocket 站内信推送	P1	⬜
8	仿真层班次调度 + 产线故障传导	P1	⬜
9	简历收尾 + 投递	P0	⏳ 持续
| 9 | **AI Agent MVP（Mock 版）** | **P0** | **✅ v1.6** |
| 10 | **接 DeepSeek 真 API** | **P0** | **⬜ v1.7** |
第二阶段：L2 微服务（目标：1-2 个月）
顺序	项目	优先级	工作量
10	微服务拆分：Nacos + Gateway + OpenFeign	P1	3-4 周
11	链路追踪：SkyWalking	P2	1 周
### 第三阶段：L3 差异化（核心目标）

| 顺序 | 项目 | 优先级 | 工作量 |
|------|------|--------|--------|
| 12 | **AI Agent MVP** | **P0** | **✅ v1.6** |
| 13 | **接 DeepSeek 真 API** | **P0** | **1 天** |
| 14 | **告警 MQ 消费者（钉钉/邮件）** | **P1** | **2 天** |
| 15 | **RAG 文档问答** | **P1** | **1 周** |
| 16 | **多轮工具编排** | **P1** | **1 周** |
| 17 | **评估体系** | **P1** | **3 天** |
分级说明
L0 能跑：功能通，单人能用

L1 能扛：并发 + 容错 + 可观测 + 配置管理

L2 能扩：微服务 + 网关 + 配置中心

L3 能演：可观测 + 可回滚 + 有 SLA

十五、Day 2 复盘（v1.5）
完成的核心闭环
text
仿真层（100 台设备，物理模型 + 故障 + 保护）
  ↓ MQTT（持久会话 + 补发 + 幂等）
主系统（订阅 + 攒批 + 消费）
  ↓
MySQL（主链路）+ TDengine（旁路，双写一致）
关键认知
cleanSession 是 MQTT 持久会话的核心：true 掉线丢消息，false 补发

MQTT 回调是单线程的：回调里不能做 IO（日志、Redis）

EMQX offline 队列有容量限制：max_mqueue_len 必须调大

TDengine 超表是子表的前置依赖：清库后必须自动重建

多模块工程的依赖隔离：仿真模块只依赖 MQTT + Jackson，不引入 web/mysql/redis

离线队列是双刃剑：好处是不丢消息，坏处是重连时一次性推大量消息

数据指标
设备数：100 台

MQTT 速率：100 条/秒

双写一致性：100%

死信：0

补发：3200 条 < 1 秒入库

踩坑数量
Day 2 新增 8 个坑（BUG_LOG 问题 69-76）

累计 76 个坑

## 十六、Day 3 复盘（v1.6）

### 完成的核心闭环

```text
MQTT 遥测 → TelemetryQueue → TelemetryBatchWriter（攒批 + 双写）
                                    ↓
                            MySQL 入库后（异步）
                                    ↓
                            AI Agent（规则引擎 + LLM 研判）
                                    ↓
                ┌──────────────────┼──────────────────┐
                ↓                  ↓                  ↓
        查历史（MySQL）       发告警（MQ）       写日志（MySQL）
                                    ↓
                          ai.analysis_log + alert_record
关键认知
规则引擎 + LLM 双层架构：规则过滤 99% 正常数据，LLM 只处理异常片段

异步边界防御性拷贝：@Async 方法入口必须拷贝 List，遍历拷贝后的集合

Redis SETNX 去重：ai:agent:dedup:{deviceId}:{ruleName}，TTL 5 分钟

JSON 字段不手拼：用 Jackson 序列化，不用字符串拼接

MQ 消息发 JSON：Spring AMQP 默认转换器不支持 POJO

测试金字塔：单元测试（毫秒级）+ 端到端测试（分钟级）+ 数据对账

规则引擎覆盖缺口：故障注入 ≠ 一定触发规则，要看物理演化是否到阈值

数据驱动迭代：从实际数据中发现规则覆盖缺口

数据指标
单元测试：5 个用例，31ms 全过

端到端：注入 10 个故障，AI Agent 处理 8 个

数据对账：24 条 AI 分析 = 8 WARNING + 16 CRITICAL

告警：16 条（8 TEMP_GT_95 + 8 STATE_FAULT）

踩坑数量
Day 3 新增 8 个坑（BUG_LOG 问题 77-84）

累计 84 个坑

十六、文档清单（10 份）
文档	用途	版本
README.md	项目说明、快速启动	v1.5
API.md	接口文档	v1.5
DATABASE.md	数据库设计	v1.5
DEPLOYMENT.md	部署与运维手册	v1.5
ENVIRONMENT.md	开发环境清单	v1.5
BUG_LOG.md	踩坑日志（76 条）	v1.5
PROGRESS.md	项目进度与架构蓝图	v1.5
ARCHITECTURE.md	架构与原理笔记	v1.5
RBAC_DESIGN.md	RBAC 权限模型设计	v1.4
| **AI_AGENT_DESIGN.md** | **AI Agent 设计文档** | **v1.0（新增）** |