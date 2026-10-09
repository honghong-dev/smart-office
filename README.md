# Smart-Office 企业级智慧协同办公平台

基于 SpringBoot 3 + SpringCloud 2023 + MyBatis-Plus + MySQL + Redis + RabbitMQ + EMQX + Redisson + Nacos + **TDengine** + Prometheus + Grafana 的企业级后端系统。

已完成 Docker Compose 一键部署，接入 MQTT 工业物联网数据链路（QoS 1 + 持久会话 + 幂等 + 批量插入 + 死信），以及审批流延迟队列、分布式锁、三级权限管理、审计日志、数据权限、Nacos 配置管理、Prometheus + Grafana 监控，**MySQL + TDengine 时序库双写**。

---

## 技术栈

| 分类 | 技术 |
|------|------|
| 后端框架 | SpringBoot 3.3.5, Spring MVC, SpringCloud 2023.0.3 |
| 持久层 | MyBatis-Plus 3.5.5, MySQL 8.0 |
| **时序数据库** | **TDengine 3.3（REST 连接，多子表合并 INSERT）** |
| 缓存 | Redis |
| 消息队列 | RabbitMQ 3.13.7 + Erlang 26 |
| 分布式锁 | Redisson 3.27.2 |
| 安全 | JWT (jjwt 0.11.5), BCrypt (强度 12) |
| 权限模型 | RBAC（用户-角色-权限），三级管理，接口级鉴权，数据权限 |
| 配置中心 | Nacos 2.3.2 |
| 监控 | Prometheus + Grafana |
| 工业物联网 | EMQX (MQTT Broker), Eclipse Paho MQTT Client 1.2.5 |
| 容器化 | Docker Compose + WSL 2 |
| 构建工具 | Maven 3.9+ |

---

## 已完成功能

### 用户与安全

- 用户注册（BCrypt 加密，强度 12，注册自动分配 USER 角色）
- 用户登录（JWT 无状态鉴权，严格校验 Bearer 前缀）
- 分页查询（Redis 缓存 + 防缓存穿透 + 数据权限）
- 统一返回格式（Result<T>）
- 全局异常处理（GlobalExceptionHandler）

### 权限与审批

- RBAC 权限模型（用户-角色-权限，三张表 + 两张关联表）
- 三级管理：老板（SUPER_ADMIN）、厂长（ADMIN）、班长（SUB_ADMIN）、员工（USER），越级拦截
- 注册自动分配 USER 角色，新用户有基础权限
- 登录时权限写入 Redis（30分钟过期）
- 自定义 @RequiresPermission 注解
- JwtInterceptor 接口级权限校验（无权限返回 403）
- 权限缓存刷新（Redis Pub/Sub）：权限变更秒级生效，用户不用重新登录
- 审批流提交接口（事务控制 + 审批记录 + MQ 异步通知）
- 审批流延迟队列：TTL + 死信队列，30 分钟超时提醒，消费端 Redis 幂等
- 分布式锁（Redisson）：审批处理并发控制，同一审批单，串行处理
- 审计日志：记录权限变更、审批处理，操作人、目标、旧值、新值、IP、时间
- 数据权限：加部门表，用户表加 dept_id，非管理员只能看本部门

### 消息队列

- RabbitMQ 异步解耦（注册后异步通知）
- RabbitMQ 延迟队列（TTL + 死信队列，审批超时提醒）
- 生产者 + 消费者完整链路

### 工业物联网（MQTT）

- Docker 启动 EMQX Broker
- Java 后端集成 Paho MQTT，订阅 factory/device/+/temperature
- QoS 1 + 持久会话（cleanSession=false + 固定 clientId），掉线期间消息不丢
- Redis SETNX 幂等，QoS 1 重复投递不重复入库
- 线程池 + 批量插入（设计 1000 条/批，实际超时触发 50~150 条/批，4 线程并行消费）
- 死信记录（解析失败、入库失败不丢弃）
- 温度超过 80℃ 自动告警
- 压测：50000 条消息，EMQX 消息丢弃 0

### 时序库双写（TDengine）

- MySQL 主链路 + TDengine 旁路，异常隔离
- 主链路失败整批进死信；旁路失败只记死信，不影响主链路
- TDengine 采用 REST 连接（jdbc:TAOS-RS://），不依赖本地客户端
- 多子表合并 INSERT（一条 SQL 写多个子表），单批 8~10ms
- 压测：5 万条，两边数据一致（49802 = 49802），无死信

### 配置管理（Nacos）

- Nacos 容器化部署
- 配置从本地 application.yml 迁移到 Nacos
- 多环境配置（dev / docker / prod）
- @RefreshScope 动态刷新
- @PreDestroy 管理 MQTT 连接生命周期
- 环境变量占位（本地用 localhost，Docker 用服务名）

### 监控体系（Prometheus + Grafana）

- SpringBoot 通过 Micrometer 暴露 /actuator/prometheus 端点
- Prometheus 采集指标，存时序数据
- Grafana 可视化：JVM、HTTP、Redis 大盘
- 告警规则 3 条：
  - HTTP 5xx 错误率 > 1%，持续 1 分钟
  - JVM 堆内存 > 85%，持续 5 分钟
  - 服务下线，持续 1 分钟

### 工程化

- Dockerfile 编写 + 镜像构建 + 容器运行
- Docker Compose 编排：一键启动 **9 个**服务（MySQL、Redis、RabbitMQ、EMQX、Nacos、**TDengine**、SpringBoot、Prometheus、Grafana）
- 环境变量覆盖：MQTT_HOST、REDIS_HOST、**TDENGINE_HOST**、SPRING_CLOUD_NACOS_CONFIG_SERVER_ADDR 等
- 完整的 8 份项目文档

---

## 项目结构

```text
com.example.smart_office
├── annotation (RequiresPermission)
├── common (Result, GlobalExceptionHandler)
├── config (WebConfig, MybatisPlusConfig, RabbitMQConfig, MqttConfig, MyMetaObjectHandler, PermissionCacheRefreshListener)
├── controller (UserController, ApprovalController)
├── entity (SysUser, SysRole, SysPermission, SysUserRole, SysApproval, SysApprovalRecord, DeviceTelemetry, SysAuditLog, SysDept, dto/)
├── interceptor (JwtInterceptor)
├── mapper (SysUserMapper, SysRoleMapper, SysPermissionMapper, SysUserRoleMapper, SysApprovalMapper, SysApprovalRecordMapper, DeviceTelemetryMapper, SysAuditLogMapper, SysDeptMapper)
├── service (SysUserService, SysApprovalService, AuditLogService, RegisterMessageConsumer, ApprovalTimeoutConsumer, MqttSubscriber, TelemetryQueue, TelemetryBatchWriter, MqttIdempotentService, DeadLetterService, TdengineWriteService, impl/)
└── utils (JwtUtils)
```

详细文档
API.md：接口文档

DATABASE.md：数据库设计

DEPLOYMENT.md：部署与运维手册

ENVIRONMENT.md：开发环境清单

BUG_LOG.md：踩坑日志（68 条）

PROGRESS.md：项目进度与架构蓝图

ARCHITECTURE.md：架构与原理笔记

RBAC_DESIGN.md：RBAC 权限模型设计

快速启动（Docker 一键部署）
1. 打包
bash
mvn clean package -DskipTests
2. 启动
bash
docker compose up -d --build
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

smart-office-app

smart-office-prometheus

smart-office-grafana

4. 验证接口
text
POST http://localhost:8081/api/user/login
Body: {"username": "admin1", "password": "123456"}
返回 Token，说明接口正常。

5. 访问各服务
服务	地址	账号密码
SpringBoot	http://localhost:8081	-
Nacos	http://localhost:8848/nacos	nacos / nacos
Prometheus	http://localhost:9090	-
Grafana	http://localhost:3000	admin / admin123
RabbitMQ	http://localhost:15673	admin / 123456
EMQX	http://localhost:18084	admin / admin123456
测试 MQTT 设备上报
用 MQTTX 连接 mqtt://宿主机真实IP:1884，用户名密码为 EMQX 的 admin / admin123456。

发布 Topic：factory/device/001/temperature。

Payload（必须带 timestamp）：

json
{"deviceId":"001","temperature":85.5,"timestamp":1735689600000}
观察 docker logs smart-office-app，应打印：

text
批量插入 X 条，耗时 Yms
TDengine 实际写入 X 条（Y 个设备），耗时 Zms
去 DBeaver 查 device_telemetry 表，应有新记录。

去 TDengine 查 SELECT * FROM smart_office.telemetry LIMIT 5;，应有对应数据。

下一步计划（按依赖排序）
第一阶段：L1 收尾（目标：1 个月）
顺序	项目	优先级	依赖	工作量	状态
1	Nacos 配置管理：多环境 + 动态刷新	P0	无	1 周	✅
2	监控体系：Prometheus + Grafana	P0	Nacos	1 周	✅
3	TDengine 双写	P0	无	1 周	✅
4	时序库查询切换（冷热分离）	P0	TDengine 双写	3-5 天	⬜
5	简历收尾 + 投递	P0	无	持续	⬜
6	WebSocket 站内信推送	P1	无	3-5 天	⬜
第二阶段：L2 微服务（目标：1-2 个月）
顺序	项目	优先级	依赖	工作量
7	微服务拆分：Nacos + Gateway + OpenFeign	P1	Nacos	3-4 周
8	链路追踪：SkyWalking	P2	微服务	1 周
第三阶段：L3 差异化（目标：2-3 个月）
顺序	项目	优先级	依赖	工作量
9	AI Agent 层：RAG + 工具调用	P2	微服务	3-4 周
分级说明
L0 能跑：功能通，单人能用

L1 能扛：并发 + 容错 + 可观测 + 配置管理

L2 能扩：微服务 + 网关 + 配置中心

L3 能演：可观测 + 可回滚 + 有 SLA