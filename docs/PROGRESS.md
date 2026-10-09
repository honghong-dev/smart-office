# Smart-Office 项目进度与架构蓝图

本文档记录 Smart-Office 项目从零开发到容器化、工业物联网接入、配置管理、监控体系的完整进度。

---

## 一、项目定位

- **项目名称**：企业级智慧协同办公平台（Smart-Office）
- **技术栈**：SpringBoot 3.3.5 + SpringCloud 2023 + MyBatis-Plus 3.5.5 + MySQL 8.0 + Redis + JWT + RabbitMQ 3.13.7 + Redisson 3.27.2 + Docker + Docker Compose + EMQX + Paho MQTT 1.2.5 + Nacos 2.3.2 + **TDengine 3.3** + Prometheus + Grafana
- **目标**：打造一个具备高并发、高可用、可扩展的工业级后端系统，作为通往工业物联网方向的实战跳板。
- **设计来源**：按长城汽车冲焊车间真实工业场景设计，实现"工业物联网数据采集 + 三级权限管理 + **时序数据存储**"最小闭环。

---

## 二、最新项目结构

```text
com.example.smart_office
├── annotation (RequiresPermission 自定义权限注解)
├── common (Result, GlobalExceptionHandler)
├── config (WebConfig, MybatisPlusConfig, RabbitMQConfig, MqttConfig, MyMetaObjectHandler, PermissionCacheRefreshListener)
├── controller (UserController, ApprovalController)
├── entity (SysUser, SysRole, SysPermission, SysUserRole, SysApproval, SysApprovalRecord, DeviceTelemetry, SysAuditLog, SysDept, dto/)
├── interceptor (JwtInterceptor)
├── mapper (SysUserMapper, SysRoleMapper, SysPermissionMapper, SysUserRoleMapper, SysApprovalMapper, SysApprovalRecordMapper, DeviceTelemetryMapper, SysAuditLogMapper, SysDeptMapper)
├── service (SysUserService, SysApprovalService, AuditLogService, RegisterMessageConsumer, ApprovalTimeoutConsumer, MqttSubscriber, TelemetryQueue, TelemetryBatchWriter, MqttIdempotentService, DeadLetterService, TdengineWriteService, impl/)
└── utils (JwtUtils)

resources
├── application.yml（本地最小配置：Nacos 地址 + 应用名 + 环境）
├── bootstrap.yml（SpringCloud Bootstrap 配置）
└── application-docker.yml.bak（本地完整配置备份，SpringBoot 不读）
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
MQTT 设备接入	EMQX Broker + QoS 1 + 持久会话 + Redis 幂等 + 线程池 + 批量插入 + 死信 + 50000 条压测	✅ 工业级
TDengine 双写	MySQL 主链路 + TDengine 旁路，异常隔离，多子表合并 INSERT，压测 5 万条两边一致	✅
权限缓存刷新	Redis Pub/Sub，权限变更秒级生效	✅
审计日志	记录权限变更、审批处理，操作人、目标、旧值、新值、IP、时间	✅
数据权限	加部门表，用户表加 dept_id，非管理员只能看本部门	✅
Nacos 配置管理	多环境配置 + 动态刷新 + @RefreshScope + @PreDestroy	✅
监控体系	Prometheus + Grafana + 告警规则（HTTP 5xx、JVM 内存、服务下线）	✅
文档	API/DB/DEPLOY/ENV/BUG_LOG/PROGRESS/ARCHITECTURE/RBAC_DESIGN/README	✅
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

☑ 测试通过：老板改自己报错、厂长改班长成功、班长改员工成功、班长改厂长报错

4.3 权限缓存刷新（Redis Pub/Sub）
☑ 改权限后，发消息到 permission:refresh 频道

☑ 各 Java 实例订阅频道，收到消息后清缓存

☑ JwtInterceptor 缓存未命中时，查库重建

☑ 测试通过：权限变更秒级生效，用户不用重新登录

4.4 数据权限（部门隔离）
☑ 创建 sys_dept 表，sys_user 表加 dept_id

☑ assignDept 接口，分配部门

☑ getUserPage 加数据权限：非管理员，加 WHERE dept_id = 当前用户的部门

☑ cacheKey 加 userId，避免缓存串数据

☑ 测试通过：非管理员只看到本部门，管理员看到所有

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

☑ 消费端 Redis SETNX 幂等，防重复提醒

☑ 测试通过：30 分钟后消息进死信队列，消费者发提醒；重复投递被幂等拦住

5.3 分布式锁（Redisson）
☑ 引入 Redisson 依赖

☑ handleApproval 接口加锁：approval:lock:{approvalId}

☑ 拿到锁后，查审批单状态，已处理则跳过

☑ 测试通过：两个请求同时发，一个报"审批单正在处理中"（锁生效），一个报"审批单已处理"（状态校验生效）

六、MQTT 设备接入（已完成，工业级）
6.1 基础链路
☑ 用 Docker 启动 EMQX Broker

☑ 用 MQTTX 模拟设备，发布温度数据到 factory/device/001/temperature

☑ Java 后端集成 Paho MQTT 客户端，订阅 factory/device/+/temperature

☑ 成功接收设备上报数据，写入 device_telemetry 表

☑ 温度超过 80℃ 自动告警

6.2 工业级改造
☑ 去掉 Thread.sleep(1000)，改用 MqttCallbackExtended.connectComplete() 回调订阅

☑ QoS 1 + 持久会话（cleanSession=false + 固定 clientId）

☑ Redis SETNX 幂等（key：mqtt:idempotent:{deviceId}:{timestamp}）

☑ 线程池 + 批量插入（4 线程，1000 条/批，1 秒超时兜底）

☑ 队列空了就 flush（防批量尾巴滚动）

☑ 死信记录（解析失败、入库失败不丢弃）

☑ report_time 自动填充（MyMetaObjectHandler）

☑ device_telemetry 加索引（idx_device_time）

☑ EMQX 参数调优（max_mqueue_len、max_inflight）

6.3 压测数据
指标	数值
压测条数	50000
EMQX 消息丢弃	0
批量插入	设计 1000 条/批，实际超时触发 50~150 条/批，MySQL 单批 10~30ms
消费线程	4 线程并行
MySQL 入库	49802
TDengine 入库	49802
两边一致性	✅ 完全一致
七、时序库双写（已完成，v1.4 新增）
7.1 架构设计
☑ MySQL 主链路 + TDengine 旁路，异常隔离

☑ 主链路失败整批进死信；旁路失败只记死信，不影响主链路

☑ REST 连接（jdbc:TAOS-RS://），不依赖本地客户端

7.2 写入优化
☑ 按设备分组，多子表合并 INSERT（一条 SQL 写多个子表）

☑ 单批 8~10ms，反超 MySQL 的 10~30ms

☑ 子表按设备自动建表（USING ... TAGS，幂等）

7.3 数据一致性保障
☑ 时间戳唯一（压测脚本用 baseTs + i 全局递增，避免 TDengine 主键覆盖）

☑ reportTime 显式设置（不依赖 MyBatis-Plus 自动填充）

☑ 死信 message_id 用 topic + nanoTime，保证唯一

☑ DeadLetterService.record 内部 catch 所有异常，不抛

7.4 压测数据
指标	数值
压测条数	50000
MySQL 入库	49802
TDengine 入库	49802
TDengine 单批耗时	8~10ms
MySQL 单批耗时	10~30ms
两边一致性	✅ 完全一致
死信	0
八、审计日志（已完成）
☑ 创建 sys_audit_log 表

☑ 编写 SysAuditLog 实体、SysAuditLogMapper

☑ 编写 AuditLogService，记录：操作人、操作类型、目标、旧值、新值、IP、时间

☑ assignRole、handleApproval 加审计日志

☑ 测试通过：数据库有记录

九、Nacos 配置管理（已完成）
☑ Nacos 容器化部署（Docker Compose 第 6 个服务）

☑ Nacos 连 MySQL，配置持久化

☑ 配置从本地 application.yml 迁移到 Nacos

☑ 多环境配置（dev / docker / prod）

☑ 环境变量占位（本地用 localhost，Docker 用服务名）

☑ @RefreshScope 动态刷新

☑ @PreDestroy 管理 MQTT 连接生命周期

☑ 踩坑 8 条（问题 49-56）

十、监控体系（已完成）
☑ SpringBoot 通过 Micrometer 暴露 /actuator/prometheus 端点

☑ Prometheus 采集指标，存时序数据

☑ Grafana 可视化：JVM、HTTP 大盘

☑ 告警规则 3 条：

HTTP 5xx 错误率 > 1%，持续 1 分钟

JVM 堆内存 > 85%，持续 5 分钟

服务下线，持续 1 分钟

十一、待开发模块
模块	核心功能	优先级
时序库查询切换	冷热分离、看板/趋势查询走 TDengine	⭐⭐
告警通知	Alertmanager，对接邮件/钉钉	⭐⭐
实时通知	WebSocket 站内信推送	⭐⭐
微服务拆分	Nacos + Gateway + OpenFeign	⭐
链路追踪	SkyWalking	⭐
AI Agent 层	RAG 知识库问答 + 工具调用	⭐
十二、里程碑记录
2026-09-27：完成 JWT 鉴权、Redis 缓存、RabbitMQ 异步解耦、1000 并发压测。

2026-09-28：完成 Docker 容器化部署，容器成功连接宿主机 MySQL/Redis/RabbitMQ，写入数据库成功。

2026-09-29：完成 RBAC 权限模型、审批流提交接口、MQTT 设备接入，成功接收设备上报数据。

2026-09-30：完成 MQTT 链路工业级改造。QoS 1 + 持久会话 + Redis 幂等 + 4 线程批量插入 + 死信记录。50000 条压测，EMQX 消息丢弃 0。

2026-10-01：完成四个模块：

审批流延迟队列：TTL + 死信队列 + 幂等，30 分钟超时提醒。

分布式锁：Redisson，审批处理并发控制。

权限缓存刷新：Redis Pub/Sub，权限变更秒级生效。

三级管理：老板、厂长、班长、员工，越级拦截。

2026-10-04：完成三个模块：

Docker Compose 一键部署：一键启动，环境变量覆盖。

审计日志：记录权限变更、审批处理。

数据权限：部门隔离，非管理员只能看本部门。

2026-10-04：完成 Nacos 配置管理。

Nacos 容器化部署

配置从本地 application.yml 迁移到 Nacos

多环境配置（dev / docker / prod）

@RefreshScope 动态刷新

@PreDestroy 管理 MQTT 连接生命周期

踩坑 8 条（问题 49-56）

2026-10-05：完成监控体系。

SpringBoot 暴露 /actuator/prometheus

Prometheus 采集指标

Grafana 可视化（JVM、HTTP）

告警规则 3 条（HTTP 5xx、JVM 内存、服务下线）

Docker Compose 扩展为 8 个服务

2026-10-05（下午）：完成 TDengine 时序库双写。

新增 TdengineWriteService，MySQL 主链路 + TDengine 旁路

REST 连接 + 多子表合并 INSERT 优化

排查 12 个真实坑（问题 57-68）

压测 5 万条，两边数据一致（49802 = 49802），无死信

TDengine 单批 8~10ms，反超 MySQL 10~30ms

Docker Compose 扩展为 9 个服务

2026-10-09:
✅ 单模块 → 多模块 Maven 工程重构（父 pom + 2 子模块）

✅ app 编译通过，jar 名保持 smart-office-0.0.1-SNAPSHOT.jar

✅ 部署文件归位（deploy/ 目录：docker-compose、prometheus、sql、data）

✅ Dockerfile 随 app 走

✅ 清理垃圾（BOOT-INF、.bak、application-dev.yml、旧 target）

✅ docker compose config 验证通过

✅ 多模块 Maven 工程（app + simulation）

✅ 部署文件归位（deploy/）

✅ Nacos 配置中心可用

✅ app 启动成功

✅ simulation 第一批代码（领域模型 + 10 台设备）

十三、下一步计划
第一阶段：L1 收尾（目标：1 个月）
顺序	项目	优先级	依赖	工作量	状态
1	Nacos 配置管理	P0	无	1 周	✅
2	监控体系：Prometheus + Grafana	P0	Nacos	1 周	✅
3	TDengine 双写	P0	无	1 周	✅
4	时序库查询切换（冷热分离）	P0	TDengine 双写	3-5 天	⬜
5	简历收尾 + 投递	P0	无	持续	⬜
6	Alertmanager 告警通知	P1	Prometheus	1 天	⬜
7	WebSocket 站内信推送	P1	无	3-5 天	⬜
第二阶段：L2 微服务（目标：1-2 个月）
顺序	项目	优先级	依赖	工作量
8	微服务拆分：Nacos + Gateway + OpenFeign	P1	Nacos	3-4 周
9	链路追踪：SkyWalking	P2	微服务	1 周
第三阶段：L3 差异化（目标：2-3 个月）
顺序	项目	优先级	依赖	工作量
10	AI Agent 层：RAG + 工具调用	P2	微服务	3-4 周
分级说明
L0 能跑：功能通，单人能用

L1 能扛：并发 + 容错 + 可观测 + 配置管理

L2 能扩：微服务 + 网关 + 配置中心

L3 能演：可观测 + 可回滚 + 有 SLA