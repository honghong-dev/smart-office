# Smart-Office API 接口文档

**文档版本**：v1.4
**最后更新**：2026-10-05
**对应代码版本**：Smart-Office v1.4

## 变更记录

| 版本 | 日期 | 变更内容 |
|------|------|----------|
| v1.0 | 2026-09-27 | 用户、审批基础接口 |
| v1.1 | 2026-09-29 | RBAC、MQTT 接入 |
| v1.2 | 2026-10-04 | 补 Docker Compose、审计日志、数据权限、权限缓存刷新、三级管理、assignDept 接口 |
| v1.3 | 2026-10-05 | 补 Nacos 配置管理、Prometheus 监控；修正 Broker 地址 |
| v1.4 | 2026-10-05 | 补 TDengine 时序库双写；修正 MQTT 性能指标 |

文档说明：本文档记录 Smart-Office 项目所有已开发接口。每新增一个接口，需同步更新本文档。

**配置来源**：配置从 Nacos 拉取（Data ID: `smart-office-docker.yml`）。

**时序库**：TDengine 3.3（REST 连接 `jdbc:TAOS-RS://localhost:6041/smart_office`），用于设备遥测数据存储，与 MySQL 双写。

基础路径：

- 本地 IDEA：http://localhost:8080
- Docker 容器：http://localhost:8081（宿主机 8081 映射容器 8080）

统一返回格式：所有接口均返回 Result<T> 结构：`{ "code": 200, "msg": "操作成功", "data": {...} }`

鉴权说明：除登录、注册接口外，其他接口均需在请求头携带 `Authorization: Bearer <Token>`

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

---

## 一、用户模块（User）

### 1.1 用户登录

接口描述：用户通过账号密码登录，成功后返回 JWT Token。

请求方式：POST
请求路径：`/api/user/login`
Content-Type：application/json

请求参数：

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| username | String | 是 | 用户名 |
| password | String | 是 | 密码 |

请求示例：

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
返回示例（失败）：

json
{
  "code": 500,
  "msg": "密码错误",
  "data": null
}
边界条件：

用户不存在 → 10003

密码错误 → 10002

1.2 用户注册
接口描述：新用户注册。密码使用 BCrypt 加密后入库，注册时自动分配 USER 角色，注册成功后通过 RabbitMQ 异步发送通知。

请求方式：POST
请求路径：/api/user/register
Content-Type：application/json

请求参数：

参数名	类型	必填	说明
username	String	是	用户名（唯一，不可重复）
password	String	是	密码（后端 BCrypt 加密后存储）
nickname	String	否	昵称
email	String	否	邮箱
请求示例：

json
{
  "username": "sunqi",
  "password": "123456",
  "nickname": "孙七",
  "email": "sunqi@test.com"
}
返回示例（成功）：

json
{
  "code": 200,
  "msg": "操作成功",
  "data": "注册成功"
}
返回示例（用户名重复）：

json
{
  "code": 500,
  "bizCode": 10001,
  "msg": "用户名已存在",
  "data": null
}
后台异步行为：

注册成功后，自动分配 USER 角色，新用户有基础权限（approval:submit）

注册成功后，会向 RabbitMQ 的 register.queue 队列发送一条消息

消费者 RegisterMessageConsumer 监听该队列，异步执行通知操作

1.3 用户分页查询
接口描述：分页查询用户列表。带 Redis 缓存，带数据权限，非管理员只能看本部门。

请求方式：GET
请求路径：/api/user/page
请求头：Authorization: Bearer <Token>
权限要求：user:list

请求参数：

参数名	类型	必填	默认值	说明
pageNum	Integer	否	1	当前页码
pageSize	Integer	否	10	每页条数
请求示例：

text
GET http://localhost:8080/api/user/page?pageNum=1&pageSize=10
返回示例：

json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "records": [
      {
        "id": 123,
        "username": "xiaohong",
        "nickname": "肖宏",
        "email": "xiaohong@test.com",
        "deptId": 2,
        "status": 1,
        "createTime": "2026-09-27T17:16:45",
        "updateTime": "2026-09-27T17:16:45",
        "deleted": 0
      }
    ],
    "total": 3,
    "size": 10,
    "current": 1,
    "pages": 1
  }
}
缓存机制说明：

缓存 Key 格式：user:page:{userId}:{pageNum}:{pageSize}

必须含 userId：数据权限下不同用户可见数据不同，不加 userId 会串数据

第一次请求：查 MySQL，写入 Redis（5 分钟过期）

第二次请求：命中 Redis 缓存，不再查库

空数据防穿透：若查询结果为空，写入空值（1 分钟过期）

数据权限说明：

非管理员（ADMIN 以下）：SQL 自动加 WHERE dept_id = 当前用户部门

管理员（SUPER_ADMIN / ADMIN）：不加部门条件，看全部

1.4 查询用户权限
接口描述：根据用户 ID，查询该用户的全部权限编码。用于调试和验证 RBAC 模型。

请求方式：GET
请求路径：/api/user/permissions/{userId}
请求头：Authorization: Bearer <Token>

请求示例：

text
GET http://localhost:8080/api/user/permissions/123
返回示例：

json
{
  "code": 200,
  "msg": "操作成功",
  "data": ["user:list", "user:delete"]
}
边界条件：

userId 不存在 → 10003

说明：该接口仅用于调试，正式环境中通常不对外开放。

1.5 分配用户角色（三级管理）
接口描述：给用户分配角色。三级管理：老板（SUPER_ADMIN）、厂长（ADMIN）、班长（SUB_ADMIN）、员工（USER），越级拦截。

请求方式：POST
请求路径：/api/user/assignRole
Content-Type：application/json
请求头：Authorization: Bearer <Token>
权限要求：user:assignRole

请求参数：

参数名	类型	必填	说明
userId	Long	是	目标用户 ID
roleIds	List<Long>	是	角色 ID 列表
请求示例：

json
{
  "userId": 123,
  "roleIds": [4]
}
返回示例（成功）：

json
{
  "code": 200,
  "msg": "操作成功",
  "data": "分配成功"
}
返回示例（越权）：

json
{
  "code": 500,
  "bizCode": 20001,
  "msg": "低级管理员不能修改管理员的角色",
  "data": null
}
三级管理规则：

当前用户	能改自己	能改厂长	能改班长	能改员工	能造老板
老板（SUPER_ADMIN）	❌	✅	✅	✅	❌
厂长（ADMIN）	✅	✅	✅	✅	❌
班长（SUB_ADMIN）	❌	❌	❌	✅	❌
员工（USER）	❌	❌	❌	❌	❌
注意：SUB_ADMIN 拥有 user:assignRole 权限，能调用接口。但业务逻辑做双向校验：

校验目标用户当前角色：不能是 SUPER_ADMIN、ADMIN、SUB_ADMIN

校验目标角色：不能是 SUPER_ADMIN、ADMIN

只能把员工改成班长

边界条件：

目标用户不存在 → 10003

越权操作 → 20001

造第二个老板 → 20002

幂等性：是。同角色重复分配结果一致。

后台行为：自动发消息到 permission:refresh 频道，清空目标用户的权限缓存。

1.6 分配用户部门（数据权限）
接口描述：给用户分配部门，用于数据权限隔离。分配后，非管理员用户查询用户列表时，只能看到本部门数据。

请求方式：POST
请求路径：/api/user/assignDept
Content-Type：application/json
请求头：Authorization: Bearer <Token>
权限要求：user:assignDept

请求参数：

参数名	类型	必填	说明
userId	Long	是	目标用户 ID
deptId	Long	是	部门 ID
请求示例：

json
{
  "userId": 123,
  "deptId": 2
}
返回示例（成功）：

json
{
  "code": 200,
  "msg": "操作成功",
  "data": "分配部门成功"
}
边界条件：

目标用户不存在 → 10003

部门不存在 → 10004

幂等性：是。同部门重复分配结果一致。

后台行为：

清该用户分页缓存（cacheKey 含 userId）

写审计日志（sys_audit_log）

二、审批模块（Approval）
2.1 提交审批
接口描述：用户提交一条审批申请。事务内保存审批单和审批记录，事务提交后通过 RabbitMQ 发送延迟消息（30 分钟超时提醒）。

请求方式：POST
请求路径：/api/approval/submit
Content-Type：application/json
请求头：Authorization: Bearer <Token>
权限要求：approval:submit

请求参数：

参数名	类型	必填	说明
title	String	是	审批标题
type	String	是	审批类型（请假/报销/出差）
content	String	否	审批内容
approverId	Long	是	审批人ID
请求示例：

json
{
  "title": "请假申请",
  "type": "请假",
  "content": "身体不适，请假一天",
  "approverId": 1
}
返回示例：

json
{
  "code": 200,
  "msg": "操作成功",
  "data": 1
}
幂等性：否。每次提交生成新审批单。

边界条件：

approverId 不存在 → 10003

后台异步行为：

提交成功后，发一条延迟消息到 approval.delay.queue，TTL = 30 分钟

30 分钟后，消息进死信队列 approval.dlx.queue，消费者查审批单状态：未批，发提醒；已批，跳过

消费者用 Redis SETNX 做幂等，防止重复提醒

2.2 处理审批（通过/驳回）
接口描述：审批人处理审批单（通过/驳回）。用 Redisson 分布式锁，保证同一审批单，只有一个请求能处理。

请求方式：POST
请求路径：/api/approval/handle
Content-Type：application/json
请求头：Authorization: Bearer <Token>
权限要求：approval:handle

请求参数：

参数名	类型	必填	说明
approvalId	Long	是	审批单 ID
status	Integer	是	1 通过，2 驳回
comment	String	否	审批意见
请求示例：

json
{
  "approvalId": 7,
  "status": 1,
  "comment": "同意"
}
返回示例（成功）：

json
{
  "code": 200,
  "msg": "操作成功",
  "data": "处理成功"
}
返回示例（并发冲突）：

json
{
  "code": 500,
  "bizCode": 30003,
  "msg": "审批单正在处理中，请稍后重试",
  "data": null
}
返回示例（已处理）：

json
{
  "code": 500,
  "bizCode": 30002,
  "msg": "审批单已处理",
  "data": null
}
幂等性：是。分布式锁 + 状态校验，重复调用返回 30002。

边界条件：

approvalId 不存在 → 30001

status 非 1/2 → 400

非审批人本人 → 403

并发控制说明：

Redisson 分布式锁：锁的 key 是 approval:lock:{approvalId}，同一审批单，串行处理

状态校验：拿到锁后，再查审批单状态，已处理则跳过

双保险：锁 + 状态校验，防止并发改状态

后台行为：写审计日志（sys_audit_log）

三、审计日志模块（Audit Log）
3.1 审计日志查询
接口描述：分页查询审计日志，记录权限变更、审批处理等敏感操作。

请求方式：GET
请求路径：/api/audit/page
请求头：Authorization: Bearer <Token>
权限要求：audit:list

请求参数：

参数名	类型	必填	默认值	说明
pageNum	Integer	否	1	页码
pageSize	Integer	否	10	每页条数
operatorId	Long	否	-	操作人 ID
operation	String	否	-	操作类型
请求示例：

text
GET http://localhost:8080/api/audit/page?pageNum=1&pageSize=10
返回示例：

json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "records": [
      {
        "id": 1,
        "operatorId": 1,
        "operatorName": "boss",
        "operation": "ASSIGN_ROLE",
        "targetId": 123,
        "targetType": "USER",
        "oldValue": "USER",
        "newValue": "SUB_ADMIN",
        "ip": "192.168.1.10",
        "createTime": "2026-10-04T10:00:00"
      }
    ],
    "total": 1,
    "size": 10,
    "current": 1,
    "pages": 1
  }
}
说明：

审计日志同步写入，高并发下可能成为瓶颈，v1.5 改 MQ 异步写

记录内容：操作人、操作类型、目标、旧值、新值、IP、时间

四、设备遥测模块（Device Telemetry）
4.1 设备数据上报（MQTT 通道，非 HTTP）
接口描述：设备通过 MQTT 协议向 EMQX Broker 发送温度数据，Java 后端订阅 factory/device/+/temperature 主题，接收后经幂等校验、批量入库，并双写 TDengine。

通信协议：MQTT
Broker 地址：

本地：tcp://宿主机IP:1884

Docker 内网：tcp://emqx:1883

订阅主题：factory/device/+/temperature
QoS：1
持久会话：cleanSession=false + 固定 clientId

Payload 格式：

json
{
  "deviceId": "001",
  "temperature": 85.5,
  "timestamp": 1735689600000
}
字段说明：

字段	类型	必填	说明
deviceId	String	是	设备 ID
temperature	Double	是	温度值（摄氏度）
timestamp	Long	是	消息时间戳（毫秒），用于幂等键
后台处理逻辑：

MQTT 回调线程只做入队（TelemetryQueue.offer），不碰数据库

TelemetryBatchWriter 用 4 线程并行消费，每攒够 1000 条或超时 1 秒，批量 INSERT

入库前用 Redis SETNX 做幂等校验（key：mqtt:idempotent:{deviceId}:{timestamp}）

温度 > 80℃ 时，按批次统计告警数量，不逐条打印

解析失败、入库失败的消息进死信队列（DeadLetterService），不丢弃

性能指标（压测数据）：

50000 条消息，EMQX 消息丢弃 0

批量插入设计 1000 条/批，实际超时触发 50~150 条/批，MySQL 单批 10~30ms

MySQL 与 TDengine 双写，两边数据一致（49802 = 49802），无死信

双写架构：

MySQL 主链路，TDengine 时序库旁路，异常隔离

主链路失败整批进死信，旁路失败不影响主链路

TDengine 采用多子表合并 INSERT，单批 8~10ms

详见 ARCHITECTURE.md 双写架构章节

4.2 消息处理状态查询（规划中）
接口描述：查询 mqtt_message_log 表，查看每条消息的处理状态（RECEIVED / PROCESSED / FAILED / DEAD）。

请求方式：GET
请求路径：/api/mqtt/message/log
状态：规划中，v1.5 实现

五、内部机制（非 HTTP 接口）
5.1 权限缓存刷新（Redis Pub/Sub）
频道：permission:refresh

触发：assignRole 后

行为：各 Java 实例订阅，收到后清目标用户权限缓存

效果：权限变更秒级生效，用户不用重新登录

降级：Redis 不可用时，JwtInterceptor 缓存未命中查库重建

5.2 审计日志写入
触发：assignRole、assignDept、handleApproval

记录：操作人、操作类型、目标、旧值、新值、IP、时间

存储：sys_audit_log

5.3 数据权限过滤
触发：getUserPage

规则：非管理员加 WHERE dept_id = 当前用户部门

缓存：cacheKey 含 userId，避免串数据

5.4 Nacos 配置管理
Data ID：smart-office-docker.yml

Group：DEFAULT_GROUP

动态刷新：@RefreshScope + @PreDestroy

详见 ARCHITECTURE.md 第二十节

5.5 TDengine 双写（新增）
连接：REST 连接（jdbc:TAOS-RS://）

写入：按设备分组，多子表合并 INSERT

容错：旁路失败只记死信，不影响 MySQL 主链路

一致性：压测 5 万条，两边数据一致

详见 ARCHITECTURE.md 双写架构章节

六、全局返回码
6.1 HTTP 状态码
code	说明
200	成功
400	参数校验失败
401	未授权（Token 缺失/无效/过期）
403	禁止访问（权限不足）
404	资源不存在
409	冲突（并发、重复提交）
429	请求过于频繁
500	系统异常
503	服务降级中
6.2 业务错误码（bizCode）
bizCode	说明	对应场景
10001	用户名已存在	注册
10002	密码错误	登录
10003	用户不存在	登录/查询
10004	部门不存在	assignDept
20001	越权操作	assignRole
20002	不能造第二个老板	assignRole
30001	审批单不存在	handle
30002	审批单已处理	handle
30003	审批单正在处理中	handle（锁冲突）
40001	MQTT 消息解析失败	死信
40002	设备数据入库失败	死信
七、接口状态汇总
序号	接口名称	请求方式	路径	状态
1	用户登录	POST	/api/user/login	✅
2	用户注册	POST	/api/user/register	✅（自动分配 USER 角色）
3	用户分页查询	GET	/api/user/page	✅ 数据权限
4	查询用户权限	GET	/api/user/permissions/{userId}	✅
5	分配用户角色	POST	/api/user/assignRole	✅ 三级管理
6	分配用户部门	POST	/api/user/assignDept	✅ 数据权限
7	提交审批	POST	/api/approval/submit	✅
8	处理审批	POST	/api/approval/handle	✅ 分布式锁
9	审计日志查询	GET	/api/audit/page	✅
10	设备数据上报	MQTT	factory/device/+/temperature	✅ 工业级 + TDengine 双写
11	消息处理状态查询	GET	/api/mqtt/message/log	⬜ 规划中
八、待开发接口（规划中）
序号	接口名称	请求方式	路径	预计版本
1	用户登出	POST	/api/user/logout	v1.1
2	修改用户信息	PUT	/api/user/update	v1.1
3	删除用户	DELETE	/api/user/{id}	v1.1
4	角色列表查询	GET	/api/role/list	v1.2
5	待我审批列表	GET	/api/approval/todo	v1.3
6	设备遥测数据查询	GET	/api/device/telemetry	v1.4
7	死信消息查询	GET	/api/mqtt/dead-letter	v1.5
8	部门列表查询	GET	/api/dept/list	v1.2