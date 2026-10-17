# Smart-Office 企业级智慧协同办公平台

基于 SpringBoot 3 + SpringCloud 2023 + MyBatis-Plus + MySQL + Redis + RabbitMQ + EMQX + Redisson + Nacos + TDengine + Prometheus + Grafana + DeepSeek API 的企业级后端系统。

多模块 Maven 工程（`smart-office-app` 业务模块 + `smart-office-simulation` 设备仿真模块），已实现 Docker Compose 一键部署、MQTT 工业物联网数据链路（QoS 1 + 持久会话 + 补发 + 幂等 + 批量插入 + 死信）、审批流延迟队列、分布式锁、三级权限管理、审计日志、数据权限、Nacos 配置管理、Prometheus + Grafana 监控、MySQL + TDengine 时序库双写（数据一致性已验证）、TDengine 冷热分离查询、WebSocket 站内信实时推送、以及基于 DeepSeek API 的 AI Agent 智能监控（规则引擎 + Function Calling + 告警去重）。

---

## 技术栈

| 分类 | 技术 |
|------|------|
| 后端框架 | SpringBoot 3.3.5, Spring MVC, SpringCloud 2023.0.3 |
| 持久层 | MyBatis-Plus 3.5.5, MySQL 8.0 |
| 时序数据库 | TDengine 3.3（REST 连接，多子表合并 INSERT，冷热分离） |
| 缓存 | Redis |
| 消息队列 | RabbitMQ 3.13.7 + Erlang 26 |
| 分布式锁 | Redisson 3.27.2 |
| 安全 | JWT (jjwt 0.11.5), BCrypt (强度 12) |
| 权限模型 | RBAC（用户-角色-权限），三级管理，接口级鉴权，数据权限 |
| 配置中心 | Nacos 2.3.2 |
| 监控 | Prometheus + Grafana |
| 工业物联网 | EMQX (MQTT Broker), Eclipse Paho MQTT Client 1.2.5 |
| 实时通信 | WebSocket + STOMP 协议 |
| AI 层 | DeepSeek API, Function Calling, 规则引擎, LLM 客户端抽象 |
| 容器化 | Docker Compose + WSL 2 |
| 构建工具 | Maven 3.9+ |
| 版本管理 | Git + Gitee + GitHub |

---

## 项目结构（多模块）

```text
smart-office (父 pom)
├── smart-office-app                业务模块
│   └── com.example.smart_office
│       ├── annotation              (RequiresPermission)
│       ├── common                  (Result, GlobalExceptionHandler)
│       ├── config                  (MqttConfig, RabbitMQConfig, WebSocketConfig, WebConfig, ...)
│       ├── controller              (UserController, ApprovalController, NotificationController, device/)
│       ├── entity                  (SysUser, SysRole, DeviceTelemetry, dto/, ...)
│       │   └── ai                  (AiAnalysisLog, AlertRecord)
│       ├── interceptor             (JwtInterceptor)
│       ├── mapper                  (XXXMapper)
│       │   └── ai                  (AiAnalysisLogMapper, AlertRecordMapper)
│       ├── service
│       │   ├── ai                  (AiMonitorService, RuleEngine, LlmClient, MockLlmClient, DeepSeekClient, AiToolExecutor)
│       │   ├── device              (DeviceTelemetryQueryService, TdengineQueryService)
│       │   ├── notification        (NotificationService)
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

```

核心亮点

高并发与零丢失：4 线程攒批消费 + MySQL/TDengine 双写，5 万条压测数据 100% 一致，无死信。

工业级容错：MQTT 持久会话 + 离线补发（掉线缓存补发 3200 条）；RabbitMQ 延迟队列 + 死信兜底。

冷热分离查询：新增 DeviceTelemetryQueryService，近 7 天走 TDengine，7 天前走 MySQL，自动跨边界合并。

AI Agent 真实落地：规则引擎前置过滤 + DeepSeek API 真 LLM 研判，支持 Function Calling，Redis SETNX 去重防告警风暴。

实时站内信：WebSocket + STOMP 协议，设备 CRITICAL 告警秒级推送至前端大屏。

工程安全防护：API Key 环境变量注入，.gitignore 排除，githooks/pre-commit 强制扫描防泄露。

快速启动

打包：mvn clean package -DskipTests

启动中间件：cd deploy && docker compose up -d

本地起 app（配置好环境变量 AI_LLM_PROVIDER=deepseek, DEEPSEEK_API_KEY=sk-xxx）

本地起仿真器（100 台设备发数据）

访问各服务：

SpringBoot: http://localhost:8080

Nacos: http://localhost:8848/nacos

Grafana: http://localhost:3000

EMQX Dashboard: http://localhost:18084

仿真器命令
text
inject {deviceId} {faultType}   注入故障 (BEARING_WEAR / OVERHEAT / SENSOR_DRIFT / SUDDEN_FAILURE)
reset {deviceId}                 复位
random {probability}             随机批量注入
status                           查看所有设备状态