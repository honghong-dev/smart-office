# Smart-Office 项目进度与架构蓝图

文档版本：v1.8
最后更新：2026-10-17
对应代码版本：Smart-Office v1.8

本文档记录 Smart-Office 项目从零开发到多模块、容器化、工业物联网、仿真层、数据一致性、AI Agent 的完整进度。

---

## 变更记录

| 版本 | 日期 | 变更内容 |
|------|------|----------|
| v1.0 | 2026-09-27 | 基础框架、JWT、RBAC、MQTT |
| v1.2 | 2026-10-04 | 审批流、延迟队列、审计日志、数据权限 |
| v1.3 | 2026-10-05 | Nacos、Prometheus、Grafana |
| v1.4 | 2026-10-05 | TDengine 双写 |
| v1.5 | 2026-10-09 | 多模块重构、设备仿真层、MQTT 持久会话 + 补发、数据零丢失验证 |
| v1.6 | 2026-10-13 | AI Agent 智能监控（规则引擎 + Function Calling + 告警去重） |
| v1.7 | 2026-10-15 | AI Agent 接入 DeepSeek 真 LLM；API Key 三层防护 |
| v1.8 | 2026-10-17 | 冷热分离查询、WebSocket 站内信推送、L1 彻底收官 |

---

## 一、项目定位

- 项目名称：企业级智慧协同办公平台（Smart-Office）
- 技术栈：SpringBoot 3 + SpringCloud 2023 + MyBatis-Plus + MySQL + Redis + JWT + RabbitMQ + Redisson + Docker Compose + EMQX + Nacos + TDengine + Prometheus + Grafana + DeepSeek API + WebSocket
- 目标：打造一个具备高并发、高可用、可扩展的工业级后端系统，作为通往工业物联网方向的实战跳板。
- 设计来源：按长城汽车冲焊车间真实工业场景设计。

---

## 二、最新项目结构（多模块）

```text
smart-office (父 pom, packaging=pom)
├── smart-office-app                        业务模块
│   └── com.example.smart_office
│       ├── annotation (RequiresPermission)
│       ├── common (Result, GlobalExceptionHandler)
│       ├── config (WebConfig, WebSocketConfig, MybatisPlusConfig, RabbitMQConfig, MqttConfig)
│       ├── controller (UserController, ApprovalController, NotificationController, device/)
│       ├── entity (SysUser, ..., DeviceTelemetry, dto/)
│       │   └── ai (AiAnalysisLog, AlertRecord)
│       ├── interceptor (JwtInterceptor)
│       ├── mapper (XXXMapper)
│       │   └── ai (AiAnalysisLogMapper, AlertRecordMapper)
│       ├── service 
│       │   ├── ai (AiMonitorService, RuleEngine, LlmClient, MockLlmClient, DeepSeekClient, AiToolExecutor)
│       │   ├── device (DeviceTelemetryQueryService, TdengineQueryService)
│       │   ├── notification (NotificationService)
│       │   └── ... (其他业务 Service)
│       └── utils (JwtUtils)
├── smart-office-simulation                 仿真模块（独立部署）
└── deploy                                  部署编排
```

三、已完成模块（L1 100% 闭环）
模块	功能	状态
环境搭建	JDK 17, MySQL, Redis, RabbitMQ, Docker, EMQX, TDengine	✅
用户模块	注册（BCrypt）、登录（JWT）、分页查询	✅
权限管理	RBAC + 三级管理 + 数据权限 + 接口鉴权	✅
审批流	提交 + 延迟队列（死信）+ 分布式锁（Redisson）	✅
MQTT 链路	持久会话 + 离线补发 + 幂等 + 批量插入 + 死信	✅
双写与容错	MySQL + TDengine 双写，异常隔离，零丢失	✅
冷热分离	时间路由（近7天TDengine，7天前MySQL）	✅ v1.8 新增
AI Agent	规则引擎 + DeepSeek API + Function Calling	✅ v1.7 新增
实时通知	WebSocket + STOMP 告警瞬间推送前端	✅ v1.8 新增
配置中心	Nacos 动态刷新 + @PreDestroy 生命周期	✅
监控体系	Prometheus + Grafana + 3 条告警规则	✅
工程化	Docker Compose 一键部署 + Git 双托管 + 10份文档	✅
四、核心模块详细说明（精简版）
4.1 MQTT 设备接入与零丢失验证
QoS 1 + 持久会话（cleanSession=false）+ offline 队列补发。

Redis SETNX 幂等 + 4 线程攒批（1000条/批）。

验证：掉线 30 秒，EMQX 缓存 3200 条，重连后 1 秒内全部入库。

压测：100 台设备每秒 100 条，死信 0。

4.2 时序库双写与冷热分离
MySQL 主链路 + TDengine 旁路，多子表合并 INSERT（单批 8~10ms）。

查询路由（DeviceTelemetryQueryService）：近 7 天走 TDengine，7 天前走 MySQL，跨边界自动合并。

4.3 AI Agent 智能监控（差异化核心）
规则引擎（RuleEngine）：温度>95/85、振动>10、状态 FAULT，短路返回。

LLM 客户端抽象（LlmClient）：Mock / DeepSeek 实现通过 @ConditionalOnProperty 切换。

Function Calling 工具：query_device_history、send_alert。

防风暴：Redis SETNX 去重（5 分钟 TTL）。

单元测试：RuleEngineTest，5 个用例全过（31ms）。

4.4 WebSocket 站内信
配置（WebSocketConfig）：@EnableWebSocketMessageBroker，端点 /ws。

推送（NotificationService）：SimpMessagingTemplate 广播 /topic/alerts 或点对点 /queue/user/{id}。

应用：AiMonitorService 触发 CRITICAL 告警时实时推送；审批超时推送给指定审批人。

五、里程碑记录
2026-09-27：完成 JWT 鉴权、Redis 缓存、RabbitMQ 异步解耦。
2026-09-28：完成 Docker 容器化部署。
2026-09-29：完成 RBAC 权限模型、审批流提交接口、MQTT 设备接入。
2026-09-30：完成 MQTT 链路工业级改造，5万条压测无丢弃。
2026-10-01：完成审批流延迟队列、分布式锁、三级管理。
2026-10-04：完成 Docker Compose 部署、审计日志、数据权限。
2026-10-05：完成监控体系、TDengine 时序库双写。
2026-10-09 (v1.5)：多模块重构、仿真层、数据零丢失验证。
2026-10-13 (v1.6)：AI Agent MVP（Mock版）、单元测试、数据对账。
2026-10-15 (v1.7)：AI Agent 接入 DeepSeek 真 LLM、API Key 三层防护。
2026-10-17 (v1.8)：冷热分离查询、WebSocket 实时推送。L1 彻底闭环。

六、下一步计划（求职与优化）
第一阶段：求职包装（P0）
顺序	项目	状态
1	更新文档至 v1.8	当前任务
2	简历收尾，挂 Gitee/GitHub 链接	待做
3	正式海投（Java后端/AI应用）	待做
第二阶段：锦上添花（P1）
顺序	项目	工作量
4	告警 MQ 消费者（钉钉/邮件）	2 天
5	规则引擎补全（趋势检测 + VIB_GT_5）	3 天
6	RAG 文档问答（LangChain4j / Spring AI）	1 周
7	仿真层班次调度 + 故障传导	1 周
第三阶段：L2 微服务（P2，入职前暂缓）
顺序	项目	工作量
8	微服务拆分：Nacos + Gateway + OpenFeign	3-4 周
9	链路追踪：SkyWalking	1 周
七、文档清单（10 份，全部 v1.8）
文档	用途
README.md	项目说明、快速启动
API.md	接口文档
DATABASE.md	数据库设计
DEPLOYMENT.md	部署与运维手册
ENVIRONMENT.md	开发环境清单
BUG_LOG.md	踩坑日志（92 条）
PROGRESS.md	项目进度与架构蓝图
ARCHITECTURE.md	架构与原理笔记
RBAC_DESIGN.md	RBAC 权限模型设计
AI_AGENT_DESIGN.md	AI Agent 设计文档