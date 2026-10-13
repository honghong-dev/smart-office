markdown
# Smart-Office AI Agent 设计文档

**文档版本**：v1.0
**创建日期**：2026-10-13
**对应代码版本**：Smart-Office v1.6
**作者**：宏宏（xiao-hongh）

---

## 一、背景与目标

### 1.1 为什么做 AI Agent

**问题**：工业设备监控场景下，传统的"阈值告警"有三个硬伤：

1. **只看单点，不看上下文**：温度 90℃ 可能正常（高负载），也可能异常（缓慢过热），单纯阈值无法区分
2. **告警不收敛**：同一设备持续异常，每次采样都告警 → 告警风暴
3. **无根因研判**：运维只收到"设备 X 温度高"，不知道为什么高、该怎么处理

**目标**：用 LLM 做"二次研判"，把"单点阈值告警"升级为"上下文 + 根因 + 处置建议"。

### 1.2 为什么不用"全量喂 LLM"

**朴素方案**：100 台设备每秒 100 条数据 → 全量喂 LLM。
**问题**：
- **成本爆炸**：每秒 100 次 LLM 调用，¥10 撑不过 10 分钟
- **延迟爆炸**：LLM 响应 2-5 秒，追不上数据流
- **幻觉风险**：对正常数据也可能"分析出问题"

**工业方案**：**"规则引擎前置过滤 + LLM 深度研判"双层架构**。
- 规则引擎（毫秒级）：过滤 99% 正常数据
- LLM（秒级）：只处理异常片段

### 1.3 设计原则

| 原则 | 落地 |
|------|------|
| **规则先过滤，LLM 后研判** | 规则引擎阈值 + 状态判断 |
| **异步不阻塞主链路** | `@Async` + 防御性拷贝 |
| **告警收敛** | Redis SETNX + 5 分钟冷却 |
| **可替换** | LLM 客户端抽象（Mock / DeepSeek） |
| **可观测** | 全流程日志 + `ai_analysis_log` 表 |
| **可回滚** | 关闭 `ai.llm.provider` 即停用 AI 层 |

---

## 二、整体架构

### 2.1 架构图
┌──────────────────────────────────────────────────────────────────┐
│ MQTT 遥测数据流（100 台设备 × 每秒 1 条） │
└──────────────────────────────────────────────────────────────────┘
↓
┌──────────────────────────────────────────────────────────────────┐
│ MqttSubscriber（订阅 factory/device/+/telemetry） │
│ ↓ telemetryQueue.offer() │
│ TelemetryQueue（内存队列，攒批） │
└──────────────────────────────────────────────────────────────────┘
↓
┌──────────────────────────────────────────────────────────────────┐
│ TelemetryBatchWriter（4 线程并行消费） │
│ 1. 攒批（1000 条 或 1 秒超时） │
│ 2. MySQL 批量 INSERT（主链路） │
│ 3. TDengine 批量 INSERT（旁路） │
│ 4. ★ AiMonitorService.onBatchFlushed(batch)（新增，异步） │
└──────────────────────────────────────────────────────────────────┘
↓（@Async 异步）
┌──────────────────────────────────────────────────────────────────┐
│ AiMonitorService（主 Agent） │
│ ┌────────────────────────────────────────────┐ │
│ │ 遍历 batch（防御性拷贝后的 snapshot） │ │
│ │ ↓ │ │
│ │ RuleEngine.evaluate(t) │ │
│ │ ↓ 命中 │ │
│ │ Redis SETNX 去重（key=ai:agent:dedup:...） │ │
│ │ ↓ 首次 │ │
│ │ AiToolExecutor.queryDeviceHistory() │ │
│ │ ↓ │ │
│ │ LlmClient.chat() ← 组装上下文 │ │
│ │ ↓ │ │
│ │ AiToolExecutor.sendAlert() ← 规则 CRITICAL │ │
│ │ ↓ │ │
│ │ 写 ai_analysis_log │ │
│ └────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────────┘
↓
┌──────────────────────────────────────────────────────────────────┐
│ 输出层 │
│ ├─ MySQL: ai_analysis_log（AI 分析日志） │
│ ├─ MySQL: alert_record（告警记录） │
│ └─ RabbitMQ: ai.alert.queue（告警事件，待扩展消费者） │
└──────────────────────────────────────────────────────────────────┘

text

### 2.2 分层职责

| 层 | 组件 | 职责 | 性能要求 |
|----|------|------|----------|
| **入口层** | `MqttSubscriber` | 接收 MQTT 消息，入队 | 毫秒级（不阻塞 ACK）|
| **缓冲层** | `TelemetryQueue` | 内存队列，攒批 | 无锁并发 |
| **持久层** | `TelemetryBatchWriter` | 批量入库 MySQL + TDengine | 秒级 |
| **规则层** | `RuleEngine` | 4 条规则匹配，短路返回 | 微秒级 |
| **去重层** | Redis SETNX | 5 分钟冷却 | 毫秒级 |
| **编排层** | `AiMonitorService` | 组装上下文 → 调 LLM → 执行工具 | 秒级 |
| **LLM 层** | `LlmClient` | Mock / DeepSeek 可替换 | 2-5 秒 |
| **工具层** | `AiToolExecutor` | 查历史、发告警 | 毫秒级 |
| **输出层** | MySQL + RabbitMQ | 持久化 + 异步通知 | 毫秒级 |

---

## 三、核心组件

### 3.1 RuleEngine（规则引擎）

**职责**：毫秒级过滤，从海量遥测数据中筛出"疑似异常"。

**规则定义**：

| 规则名 | 条件 | 级别 | 优先级 |
|--------|------|------|--------|
| `STATE_FAULT` | `state == "FAULT"` | CRITICAL | 1（最高）|
| `TEMP_GT_95` | `temperature > 95` | CRITICAL | 2 |
| `VIB_GT_10` | `vibration > 10` | CRITICAL | 3 |
| `TEMP_GT_85` | `temperature > 85` | WARNING | 4 |

**关键设计决策**：

1. **短路返回**：命中第一个规则就返回，不继续判断
    - 理由：一台设备同一时刻只需处理一个最严重的异常
    - 好处：性能最优，规则语义清晰
2. **优先级顺序**：`FAULT > 温度 > 振动 > 预警`
    - 理由：`FAULT` 是最终态，优先级最高；温度是工业设备最敏感的指标
3. **阈值对齐仿真层保护域**：
    - 仿真层：温度 > 95℃ 降载，> 105℃ 停机；振动 > 10mm/s 停机
    - 规则引擎：温度 > 95 CRITICAL，> 85 WARNING；振动 > 10 CRITICAL
    - **对齐逻辑**：规则引擎在仿真层保护动作触发前就预警

**返回结构**：

```java
public static class RuleHit {
    private final String deviceId;
    private final String ruleName;      // STATE_FAULT / TEMP_GT_95 / VIB_GT_10 / TEMP_GT_85
    private final String triggerValue;  // 触发值（如 "96.3"）
    private final String level;         // WARNING / CRITICAL
}
方法签名：

java
public Optional<RuleHit> evaluate(DeviceTelemetry t);
3.2 LlmClient（LLM 客户端抽象）
职责：抽象 LLM 调用，实现可替换。

接口：

java
public interface LlmClient {
    String chat(String systemPrompt, String userPrompt);
}
实现：

实现	说明
MockLlmClient	开发调试用，返回固定响应，不花钱
DeepSeekClient	生产用，调 DeepSeek API（待实现）
切换方式（配置驱动）：

yaml
ai:
  llm:
    provider: mock   # mock / deepseek
关键设计决策：

接口先于实现：先定义接口，Mock 跑通逻辑，再换 DeepSeek

@ConditionalOnProperty：通过配置切换实现，Spring Boot 自动装配

好处：

开发阶段不花钱、不依赖网络、响应秒回

跑通后换 DeepSeek，代码不变，只换实现

3.3 AiToolExecutor（工具执行器）
职责：执行 LLM 请求的工具。

工具清单：

工具名	入参	行为	幂等
query_device_history	deviceId, limit	查 MySQL device_telemetry 最近 N 条	是
send_alert	deviceId, level, reason, aiLogId	写 alert_record + 发 ai.alert.queue	是
关键设计决策：

工具幂等：LLM 可能重复调用工具（多轮对话），工具必须幂等

query_device_history：天然幂等

send_alert：通过 aiLogId 关联 + Redis 去重保证

MQ 消息用 JSON：objectMapper.writeValueAsString(record)

理由：Spring AMQP 默认 SimpleMessageConverter 只支持 String/byte[]/Serializable

JSON 跨语言兼容、管理台可读、字段演进灵活

异常隔离：MQ 发送失败不影响 MySQL 已写入的部分

try-catch 包住 MQ 发送，失败打 WARN，不抛异常

3.4 AiMonitorService（主 Agent）
职责：编排整个 AI 分析流程。

核心方法：

java
@Async
public void onBatchFlushed(List<DeviceTelemetry> batch);
处理流程：

text
1. 防御性拷贝 snapshot = new ArrayList<>(batch)
2. 遍历 snapshot 每条数据
3. RuleEngine.evaluate(t) → Optional<RuleHit>
4. 命中 → Redis SETNX 去重（key=ai:agent:dedup:{deviceId}:{ruleName}，TTL 5 分钟）
5. 首次 → 组装 prompt → LlmClient.chat()
6. LLM 返回 → 按级别调 AiToolExecutor.sendAlert()（CRITICAL 才发）
7. 写 ai_analysis_log
关键设计决策：

@Async 异步：不阻塞 TelemetryBatchWriter.flush() 主链路

防御性拷贝：new ArrayList<>(batch) 避免主线程 batch.clear() 导致 ConcurrentModificationException

Redis SETNX 去重：同一设备同一规则 5 分钟内只处理一次，防告警风暴

异常吞掉 + 日志：单条数据处理失败不影响批量处理

四、数据模型
4.1 ai_analysis_log（AI 分析日志）
用途：记录每次 AI 分析的完整过程（可审计、可追溯）。

sql
CREATE TABLE `ai_analysis_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `device_id` VARCHAR(64) NOT NULL COMMENT '设备ID',
  `trigger_rule` VARCHAR(128) NOT NULL COMMENT '触发规则',
  `trigger_value` VARCHAR(64) DEFAULT NULL COMMENT '触发值',
  `context_json` JSON DEFAULT NULL COMMENT '喂给LLM的上下文',
  `llm_response` TEXT COMMENT 'LLM最终输出',
  `tool_calls` JSON DEFAULT NULL COMMENT 'LLM调用的工具列表',
  `analysis_result` VARCHAR(32) DEFAULT NULL COMMENT '研判结论：WARNING/CRITICAL',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_device_time` (`device_id`, `created_at`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI分析日志';
字段说明：

字段	用途
context_json	完整保存喂给 LLM 的上下文，可复现
llm_response	LLM 最终输出，可审计
tool_calls	LLM 请求调用的工具列表，JSON 格式
analysis_result	研判结论，与 trigger_rule 配合
4.2 alert_record（告警记录）
用途：记录告警事件，供运维处理。

sql
CREATE TABLE `alert_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `device_id` VARCHAR(64) NOT NULL,
  `level` VARCHAR(16) NOT NULL COMMENT 'INFO/WARNING/CRITICAL',
  `reason` VARCHAR(512) NOT NULL,
  `source` VARCHAR(32) NOT NULL DEFAULT 'AI_AGENT' COMMENT 'RULE/AI_AGENT',
  `ai_log_id` BIGINT DEFAULT NULL COMMENT '关联的AI分析日志ID',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0未处理 1已处理 2已忽略',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `handled_at` DATETIME DEFAULT NULL,
  `handled_by` VARCHAR(64) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_device_time` (`device_id`, `created_at`),
  KEY `idx_status` (`status`),
  KEY `idx_level` (`level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警记录';
关键设计决策：

source 区分告警来源（纯规则 / AI Agent）

status 支持告警处理流转

ai_log_id 关联 AI 分析日志，可追溯

五、RabbitMQ 拓扑
5.1 拓扑结构
text
Producer: AiToolExecutor.sendAlert()
   ↓
Exchange: ai.alert.exchange (DirectExchange, durable)
   ↓ routingKey = "ai.alert"
Queue: ai.alert.queue (durable)
   ├─ 属性：
   │   ├─ x-dead-letter-exchange: ai.alert.dlx.exchange
   │   └─ x-dead-letter-routing-key: ai.alert.dlx
   ↓ 消费失败 / TTL 过期
Exchange: ai.alert.dlx.exchange (DirectExchange)
   ↓
Queue: ai.alert.dlx.queue (死信队列)
5.2 配置项
组件	名称	类型	说明
Exchange	ai.alert.exchange	DirectExchange	告警主交换机
Queue	ai.alert.queue	durable	告警队列
RoutingKey	ai.alert	-	路由键
DLX Exchange	ai.alert.dlx.exchange	DirectExchange	死信交换机
DLX Queue	ai.alert.dlx.queue	durable	死信队列
DLX RoutingKey	ai.alert.dlx	-	死信路由键
5.3 为什么带死信
理由：

告警发到 MQ 后，未来会有消费者（钉钉/邮件/短信）

消费者处理失败（网络抖动、第三方限流）→ 进死信队列

死信可重试、可人工介入、可审计

这是工业级 MQ 设计的标准做法 —— 所有关键队列必须带死信。

六、可靠性设计
6.1 防告警风暴（Redis SETNX 去重）
问题：一台设备持续异常，每批数据都触发 AI Agent → 告警刷屏。

方案：

java
String dedupKey = "ai:agent:dedup:" + deviceId + ":" + ruleName;
Boolean isFirst = redisTemplate.opsForValue()
        .setIfAbsent(dedupKey, "1", Duration.ofMinutes(5));
if (Boolean.FALSE.equals(isFirst)) {
    continue;   // 5 分钟内已处理，跳过
}
语义：

首次命中 → SETNX 成功 → 处理 + 告警

5 分钟内重复命中 → SETNX 失败 → 跳过

5 分钟后 → TTL 过期 → 允许重新触发（应对"恢复后再次异常"场景）

面试话术：

"工业告警系统必须有'去重冷却'机制，避免告警风暴。我用 Redis SETNX + TTL 实现分布式去重，key 是 ai:agent:dedup:{deviceId}:{ruleName}，TTL 5 分钟。这样既避免刷屏，又保证'恢复后再次异常'能重新触发。"

6.2 异步边界防御性拷贝
问题：@Async 方法拿到的是 List 引用，主线程 batch.clear() 会触发 ConcurrentModificationException。

方案：

java
@Async
public void onBatchFlushed(List<DeviceTelemetry> batch) {
    List<DeviceTelemetry> snapshot = new ArrayList<>(batch);   // 拷贝
    for (DeviceTelemetry t : snapshot) {   // 遍历拷贝
        // ...
    }
}
关键：

拷贝本身不够，遍历时必须用拷贝后的集合

常见坑：new ArrayList<>(batch) 后 for (DeviceTelemetry t : batch) —— 拷了白拷

面试话术：

"@Async 方法拿到的是引用传递，主线程如果在异步执行期间修改集合会抛 CME。工业级做法是在异步方法入口做防御性拷贝，且遍历时必须用拷贝后的集合。"

6.3 异常隔离
失败点	处理方式	影响
规则不命中	跳过，不调 LLM	无
LLM 调用失败	打 ERROR，不写 ai_analysis_log	单条数据无分析
工具调用失败	打 ERROR，继续处理下一条	单条数据无工具结果
MQ 发送失败	打 WARN，MySQL 已写入	告警发出去了，但 MQ 没有
写 ai_analysis_log 失败	打 ERROR	单条数据无审计
关键原则：任何一环节失败都不影响主链路（MQTT → MySQL）。

6.4 降级开关
通过配置关闭 AI 层：

yaml
ai:
  llm:
    provider: mock   # 或禁用 AI 层
更彻底的方式：注释掉 TelemetryBatchWriter.flush() 里的 AI 调用。

七、测试
7.1 单元测试（com.example.smart_office.service.ai.RuleEngineTest）
5 个用例：

用例	验证点
正常数据 → 不命中	负向测试
温度 85-95 → TEMP_GT_85 WARNING	分级正确
状态 == FAULT → 优先 STATE_FAULT	优先级正确
温度 > 95 → TEMP_GT_95 CRITICAL	阈值正确
振动 > 10 → VIB_GT_10 CRITICAL	多维度判断
执行时间：31ms 全过。

关键设计决策：

测试金字塔底层：单元测试快速验证规则逻辑

边界值：95.0 不命中 TEMP_GT_95（开区间），命中 TEMP_GT_85

7.2 端到端测试
方式：仿真层注入故障 → 观察 AI Agent 处理。

测试结果：

注入 10 个故障，AI Agent 处理 8 台

发现：CONVEYOR-01-01（BEARING_WEAR，振动缓慢上升）和 ASSEMBLY-01-01（SUDDEN_FAILURE，瞬态）未触发规则

认知：端到端测试依赖物理演化，时间长且不稳定

测试分层的价值：

单元测试：验证规则正确性（毫秒级）

端到端测试：验证全链路（分钟级）

两者互补，不能互相替代

7.3 数据一致性对账
对账逻辑：

text
ai_analysis_log 总数 = WARNING + CRITICAL 总数
alert_record 总数 = CRITICAL 总数
实测数据：

规则	级别	数量
TEMP_GT_85	WARNING	8
TEMP_GT_95	CRITICAL	8
STATE_FAULT	CRITICAL	8
合计		24
对账：

ai_analysis_log = 24

alert_record = 16（8 TEMP_GT_95 + 8 STATE_FAULT）

差值 = 8（WARNING 级别） ✅

面试话术：

"我用了分层对账：数据源层（故障注入数）→ 处理层（规则命中数）→ 输出层（告警数），三层逐层对账。24 条 AI 分析 = 8 WARNING + 16 CRITICAL，16 CRITICAL 全对应到 16 条告警记录。"

八、演进路线
8.1 已完成（v1.6）
✅ 规则引擎 + LLM 双层架构

✅ Function Calling 工具（query_device_history / send_alert）

✅ LLM 客户端抽象（Mock 实现）

✅ Redis SETNX 去重

✅ @Async 异步 + 防御性拷贝

✅ 单元测试（5 个用例）

✅ 数据一致性对账