# Smart-Office API 接口文档

文档版本：v1.8
最后更新：2026-10-17
对应代码版本：Smart-Office v1.8

## 变更记录

| 版本 | 日期 | 变更内容 |
|------|------|----------|
| v1.0 | 2026-09-27 | 用户、审批基础接口 |
| v1.1 | 2026-09-29 | RBAC、MQTT 接入 |
| v1.2 | 2026-10-04 | 补 Docker Compose、审计日志、数据权限、权限缓存刷新、三级管理、assignDept 接口 |
| v1.3 | 2026-10-05 | 补 Nacos 配置管理、Prometheus 监控；修正 Broker 地址 |
| v1.4 | 2026-10-05 | 补 TDengine 时序库双写；修正 MQTT 性能指标 |
| v1.5 | 2026-10-09 | 多模块重构；MQTT 持久会话 + 补发；Topic 改 /telemetry；新增设备仿真模块；新增 Agent 规划 |
| v1.6 | 2026-10-13 | AI Agent MVP：规则引擎 + Function Calling + 工具执行 + 告警去重；新增 ai_analysis_log / alert_record 表；新增 ai.alert.queue |
| v1.7 | 2026-10-15 | AI Agent 接入 DeepSeek 真 LLM；API Key 通过环境变量注入 |
| v1.8 | 2026-10-17 | 新增冷热分离查询接口；新增 WebSocket 站内信推送端点 |

**文档说明**：本文档记录 Smart-Office 项目所有已开发接口。每新增一个接口，需同步更新本文档。

**配置来源**：配置从 Nacos 拉取（Data ID: `smart-office-docker.yml`）。

**时序库**：TDengine 3.3（REST 连接 `jdbc:TAOS-RS://localhost:6041/smart_office`），用于设备遥测数据存储，与 MySQL 双写。

**AI 层**：DeepSeek API（当前已接入真实 API），Function Calling，规则引擎前置过滤。

**基础路径**：
- 本地 IDEA：http://localhost:8080
- Docker 容器：http://localhost:8081（宿主机 8081 映射容器 8080）

**统一返回格式**：所有接口均返回 Result<T> 结构：`{ "code": 200, "msg": "操作成功", "data": {...} }`

**鉴权说明**：除登录、注册接口外，其他接口均需在请求头携带 `Authorization: Bearer <Token>`

---

## 零、全局契约（Global Contract）

### 0.1 鉴权契约
- 登录接口返回 JWT，有效期 30 分钟（与 Redis 权限缓存对齐）
- 请求头：`Authorization: Bearer <Token>`
- Token 过期返回 401，调用方需重新登录
- 权限不足返回 403，不暴露具体缺哪个权限（防探测）

### 0.2 幂等契约
| 接口 | 幂等 | 说明 |
|------|------|------|
| POST /api/user/register | 否 | 用户名唯一约束兜底 |
| POST /api/approval/submit | 否 | 每次提交生成新审批单 |
| POST /api/approval/handle | 是 | 分布式锁 + 状态校验，重复调用返回"已处理" |
| POST /api/user/assignRole | 是 | 同角色重复分配结果一致 |
| POST /api/user/assignDept | 是 | 同部门重复分配结果一致 |

### 0.3 分页契约
- pageNum 从 1 开始，pageSize 默认 10
- 返回结构固定：records / total / size / current / pages

### 0.4 超时与重试
- 普通接口：调用方超时建议 3s，不自动重试
- 审批处理：超时 5s，幂等可重试
- MQTT 上报：QoS 1，Broker 负责重投，消费端幂等

### 0.5 AI Agent 契约
- AI Agent 无对外 HTTP 接口：以内嵌服务形式挂载在 `TelemetryBatchWriter.flush()` 之后
- 触发方式：MQTT 遥测数据入库后，异步调用（`@Async`），不阻塞主链路
- 降级：规则引擎不命中 → 不调 LLM；LLM 调用失败 → 只记录日志，不影响主链路
- 去重：Redis SETNX，key = `ai:agent:dedup:{deviceId}:{ruleName}`，TTL 5 分钟

---

## 一、用户模块（User）

### 1.1 用户登录
**接口描述**：用户通过账号密码登录，成功后返回 JWT Token。
**请求方式**：POST
**请求路径**：`/api/user/login`
**Content-Type**：application/json

**请求参数**：
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| username | String | 是 | 用户名 |
| password | String | 是 | 密码 |

**请求示例**：
```json
{
  "username": "sunqi",
  "password": "123456"
}
返回示例（成功）：

json
{
  "code": 200,
  "msg": "操作成功",
  "data": "eyJhbGciOiJIUzI1NiJ9..."
}
边界条件：

用户不存在 → 10003

密码错误 → 10002

1.2 用户注册
接口描述：新用户注册。密码使用 BCrypt 加密后入库，注册时自动分配 USER 角色，注册成功后通过 RabbitMQ 异步发送通知。
请求方式：POST
请求路径：/api/user/register

请求参数：

参数名	类型	必填	说明
username	String	是	用户名（唯一，不可重复）
password	String	是	密码（后端 BCrypt 加密后存储）
nickname	String	否	昵称
email	String	否	邮箱
后台异步行为：

注册成功后，自动分配 USER 角色，新用户有基础权限（approval:submit）

会向 RabbitMQ 的 register.queue 队列发送一条消息，异步执行通知操作

1.3 用户分页查询
接口描述：分页查询用户列表。带 Redis 缓存，带数据权限，非管理员只能看本部门。
请求方式：GET
请求路径：/api/user/page
权限要求：user:list

数据权限说明：

非管理员（ADMIN 以下）：SQL 自动加 WHERE dept_id = 当前用户部门

管理员（SUPER_ADMIN / ADMIN）：不加部门条件，看全部

1.4 分配用户角色（三级管理）
接口描述：给用户分配角色。三级管理：老板、厂长、班长、员工，越级拦截。
请求方式：POST
请求路径：/api/user/assignRole
权限要求：user:assignRole

三级管理规则：

当前用户	能改自己	能改厂长	能改班长	能改员工	能造老板
老板	否	是	是	是	否
厂长	是	是	是	是	否
班长	否	否	否	是	否
员工	否	否	否	否	否
1.5 分配用户部门（数据权限）
接口描述：给用户分配部门，用于数据权限隔离。
请求方式：POST
请求路径：/api/user/assignDept
权限要求：user:assignDept

二、审批模块（Approval）
2.1 提交审批
接口描述：用户提交审批，事务内保存，事务提交后通过 RabbitMQ 发送延迟消息（30 分钟超时提醒）。
请求方式：POST
请求路径：/api/approval/submit
权限要求：approval:submit

2.2 处理审批（通过/驳回）
接口描述：审批人处理审批单。用 Redisson 分布式锁，保证同一审批单只有一个请求能处理。
请求方式：POST
请求路径：/api/approval/handle
权限要求：approval:handle

三、审计日志模块（Audit Log）
3.1 审计日志查询
接口描述：分页查询审计日志，记录权限变更、审批处理等敏感操作。
请求方式：GET
请求路径：/api/audit/page
权限要求：audit:list

四、设备遥测模块（Device Telemetry）
4.1 设备数据上报（MQTT 通道，非 HTTP）
接口描述：设备通过 MQTT 协议向 EMQX Broker 发送遥测数据，Java 后端订阅并双写 TDengine。
通信协议：MQTT
Broker 地址：tcp://localhost:1884
订阅主题：factory/device/+/telemetry
QoS：1
持久会话：cleanSession=false + 固定 clientId + session_expiry_interval=7200

Payload 格式：

json
{
  "deviceId": "PRESS-01-01",
  "deviceName": "冲压机-01-01",
  "state": "RUNNING",
  "temperature": 85.5,
  "vibration": 2.3,
  "speed": 1200,
  "timestamp": 1735689600000
}
4.2 设备遥测数据查询（v1.8 新增：冷热分离）
接口描述：根据设备 ID 和时间范围查询遥测数据。支持冷热分离路由（近 7 天查 TDengine，7 天前查 MySQL）。
请求方式：GET
请求路径：/api/device/telemetry

请求参数：

参数名	类型	必填	说明
deviceId	String	是	设备 ID，如 WELD-02-01
startTime	String	是	起始时间（yyyy-MM-dd HH:mm:ss）
endTime	String	是	截止时间（yyyy-MM-dd HH:mm:ss）
limit	Integer	否	最大返回条数（默认 100）
返回示例：

json
{
  "code": 200,
  "msg": "操作成功",
  "data": [
    {
      "deviceId": "WELD-02-01",
      "temperature": 75.2,
      "vibration": 1.5,
      "reportTime": "2026-10-17T13:12:32"
    }
  ]
}
五、WebSocket 站内信推送（v1.8 新增）
5.1 连接与订阅
接口描述：前端与后端建立 WebSocket 长连接，接收设备告警等实时消息推送。
连接协议：WebSocket (STOMP)
端点地址：ws://localhost:8080/ws

订阅频道：

频道	用途	说明
/topic/alerts	广播告警	AI Agent 触发 CRITICAL 告警时，所有人可见
/queue/user/{userId}	点对点私信	审批超时推送给指定审批人
推送消息格式：

json
{
  "title": "设备异常告警",
  "content": "设备 WELD-02-01 触发 TEMP_GT_95，值=95.65，请及时处理",
  "level": "CRITICAL",
  "timestamp": "2026-10-17T14:24:00"
}
六、内部机制（非 HTTP 接口）
6.1 权限缓存刷新（Redis Pub/Sub）
频道：permission:refresh

行为：各实例订阅，收到后清目标用户权限缓存，实现秒级生效。

6.2 AI Agent 智能监控
触发：TelemetryBatchWriter.flush() 入库后异步触发。

规则：4 条规则（温度、振动、状态）。

工具：query_device_history、send_alert。

去重：Redis SETNX。

详见 docs/AI_AGENT_DESIGN.md。

七、接口状态汇总
序号	接口名称	请求方式	路径	状态
1	用户登录	POST	/api/user/login	✅
2	用户注册	POST	/api/user/register	✅
3	用户分页查询	GET	/api/user/page	✅ 数据权限
4	分配用户角色	POST	/api/user/assignRole	✅ 三级管理
5	分配用户部门	POST	/api/user/assignDept	✅ 数据权限
6	提交审批	POST	/api/approval/submit	✅
7	处理审批	POST	/api/approval/handle	✅ 分布式锁
8	审计日志查询	GET	/api/audit/page	✅
9	设备数据上报	MQTT	factory/device/+/telemetry	✅ 双写+补发
10	遥测数据查询	GET	/api/device/telemetry	✅ v1.8 冷热分离
11	站内信推送	WebSocket	/ws	✅ v1.8 推送
12	AI Agent 监控	内嵌异步	TelemetryBatchWriter	✅ v1.7 真 LLM
```
