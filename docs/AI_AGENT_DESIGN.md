# Smart-Office AI Agent 设计文档

文档版本：v1.2
最后更新：2026-10-17
对应代码版本：Smart-Office v1.8

---

## 一、背景与目标

### 1.1 为什么做 AI Agent

**问题**：工业设备监控场景下，传统的"阈值告警"有三个硬伤：
1. **只看单点，不看上下文**：温度 90℃ 可能正常（高负载），也可能异常（缓慢过热），单纯阈值无法区分。
2. **告警不收敛**：同一设备持续异常，每次采样都告警 → 告警风暴。
3. **无根因研判**：运维只收到"设备 X 温度高"，不知道为什么高、该怎么处理。

**目标**：用 LLM 做"二次研判"，把"单点阈值告警"升级为"上下文 + 根因 + 处置建议"。

### 1.2 为什么不用"全量喂 LLM"

**朴素方案**：100 台设备每秒 100 条数据 → 全量喂 LLM。
**问题**：
- **成本爆炸**：每秒 100 次 LLM 调用，¥10 撑不过 10 分钟。
- **延迟爆炸**：LLM 响应 2-5 秒，追不上数据流。
- **幻觉风险**：对正常数据也可能"分析出问题"。

**工业方案**：**"规则引擎前置过滤 + LLM 深度研判"双层架构**。
- 规则引擎（毫秒级）：过滤 99% 正常数据。
- LLM（秒级）：只处理异常片段。

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

```text
┌──────────────────────────────────────────────────────────────────┐
│ MQTT 遥测数据流（100 台设备 × 每秒 1 条）                         │
└──────────────────────────────────────────────────────────────────┘
                              ↓
┌──────────────────────────────────────────────────────────────────┐
│ MqttSubscriber（订阅 factory/device/+/telemetry）                 │
│   ↓ telemetryQueue.offer()                                       │
│ TelemetryQueue（内存队列，攒批）                                  │
└──────────────────────────────────────────────────────────────────┘
                              ↓
┌──────────────────────────────────────────────────────────────────┐
│ TelemetryBatchWriter（4 线程并行消费）                            │
│   1. 攒批（1000 条 或 1 秒超时）                                  │
│   2. MySQL 批量 INSERT（主链路）                                  │
│   3. TDengine 批量 INSERT（旁路）                                 │
│   4. ★ AiMonitorService.onBatchFlushed(batch)（异步）             │
└──────────────────────────────────────────────────────────────────┘
                              ↓（@Async 异步）
┌──────────────────────────────────────────────────────────────────┐
│ AiMonitorService（主 Agent）                                      │
│   ┌────────────────────────────────────────────┐                 │
│   │ 遍历 batch（防御性拷贝后的 snapshot）      │                 │
│   │   ↓                                        │                 │
│   │ RuleEngine.evaluate(t)                     │                 │
│   │   ↓ 命中                                   │                 │
│   │ Redis SETNX 去重（key=ai:agent:dedup:...） │                 │
│   │   ↓ 首次                                   │                 │
│   │ AiToolExecutor.queryDeviceHistory()        │                 │
│   │   ↓                                        │                 │
│   │ LlmClient.chat()  ← 组装上下文              │                 │
│   │   ↓                                        │                 │
│   │ AiToolExecutor.sendAlert()  ← 规则 CRITICAL │                 │
│   │   ↓                                        │                 │
│   │ 写 ai_analysis_log                         │                 │
│   └────────────────────────────────────────────┘                 │
└──────────────────────────────────────────────────────────────────┘
                              ↓
┌──────────────────────────────────────────────────────────────────┐
│ 输出层                                                           │
│   ├─ MySQL: ai_analysis_log（AI 分析日志）                        │
│   ├─ MySQL: alert_record（告警记录）                              │
│   └─ RabbitMQ: ai.alert.queue（告警事件，待扩展消费者）            │
└──────────────────────────────────────────────────────────────────┘
2.2 分层职责
层	组件	职责	性能要求
入口层	MqttSubscriber	接收 MQTT 消息，入队	毫秒级（不阻塞 ACK）
缓冲层	TelemetryQueue	内存队列，攒批	无锁并发
持久层	TelemetryBatchWriter	批量入库 MySQL + TDengine	秒级
规则层	RuleEngine	4 条规则匹配，短路返回	微秒级
去重层	Redis SETNX	5 分钟冷却	毫秒级
编排层	AiMonitorService	组装上下文 → 调 LLM → 执行工具	秒级
LLM 层	LlmClient	Mock / DeepSeek 可替换	2-5 秒
工具层	AiToolExecutor	查历史、发告警	毫秒级
输出层	MySQL + RabbitMQ	持久化 + 异步通知	毫秒级
三、核心组件
3.1 RuleEngine（规则引擎）
职责：毫秒级过滤，从海量遥测数据中筛出"疑似异常"。

规则定义：

规则名	条件	级别	优先级
STATE_FAULT	state == "FAULT"	CRITICAL	1（最高）
TEMP_GT_95	temperature > 95	CRITICAL	2
VIB_GT_10	vibration > 10	CRITICAL	3
TEMP_GT_85	temperature > 85	WARNING	4
关键设计决策：

短路返回：命中第一个规则就返回，不继续判断。理由：一台设备同一时刻只需处理一个最严重的异常。

优先级顺序：FAULT > 温度 > 振动 > 预警。理由：FAULT 是最终态，优先级最高；温度是工业设备最敏感的指标。

阈值对齐仿真层保护域：规则引擎在仿真层保护动作（95℃降载/105℃停机）触发前就预警。

3.2 LlmClient（LLM 客户端抽象）
职责：抽象 LLM 调用，实现可替换。

实现：

MockLlmClient：开发调试用，返回固定响应，不花钱。

DeepSeekClient：生产用，调 DeepSeek API（v1.7 已接入）。

切换方式（配置驱动）：

yaml
ai:
  llm:
    provider: deepseek   # mock / deepseek
3.3 AiToolExecutor（工具执行器）
职责：执行 LLM 请求的工具。

工具清单：

工具名	入参	行为	幂等
query_device_history	deviceId, limit	查 MySQL device_telemetry 最近 N 条	是
send_alert	deviceId, level, reason, aiLogId	写 alert_record + 发 ai.alert.queue	是
关键设计决策：

工具幂等：LLM 可能重复调用工具，通过 aiLogId 关联 + Redis 去重保证。

MQ 消息用 JSON：objectMapper.writeValueAsString(record)。理由：Spring AMQP 默认转换器限制。

异常隔离：MQ 发送失败不影响 MySQL 已写入的部分。

3.4 AiMonitorService（主 Agent）
职责：编排整个 AI 分析流程。

核心方法：@Async public void onBatchFlushed(List<DeviceTelemetry> batch);

关键设计决策：

@Async 异步：不阻塞 TelemetryBatchWriter.flush() 主链路。

防御性拷贝：new ArrayList<>(batch) 避免主线程 batch.clear() 导致 ConcurrentModificationException。

Redis SETNX 去重：同一设备同一规则 5 分钟内只处理一次，防告警风暴。

四、数据模型
4.1 ai_analysis_log（AI 分析日志）
记录每次 AI 分析的完整过程（可审计、可追溯）。

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
4.2 alert_record（告警记录）
记录告警事件，供运维处理。

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
5.2 为什么带死信
消费者处理失败（网络抖动、第三方限流）→ 进死信队列。

死信可重试、可人工介入、可审计。

工业级 MQ 设计的标准做法 —— 所有关键队列必须带死信。

六、可靠性设计
6.1 防告警风暴（Redis SETNX 去重）
java
String dedupKey = "ai:agent:dedup:" + deviceId + ":" + ruleName;
Boolean isFirst = redisTemplate.opsForValue()
        .setIfAbsent(dedupKey, "1", Duration.ofMinutes(5));
if (Boolean.FALSE.equals(isFirst)) {
    continue;   // 5 分钟内已处理，跳过
}
面试话术："工业告警系统必须有'去重冷却'机制，避免告警风暴。我用 Redis SETNX + TTL 实现分布式去重，key 是 ai:agent:dedup:{deviceId}:{ruleName}，TTL 5 分钟。"

6.2 异步边界防御性拷贝
java
@Async
public void onBatchFlushed(List<DeviceTelemetry> batch) {
    List<DeviceTelemetry> snapshot = new ArrayList<>(batch);   // 拷贝
    for (DeviceTelemetry t : snapshot) {   // 遍历拷贝
        // ...
    }
}
面试话术："@Async 方法拿到的是引用传递，主线程如果在异步执行期间修改集合会抛 CME。工业级做法是在异步方法入口做防御性拷贝，且遍历时必须用拷贝后的集合。"

6.3 异常隔离
失败点	处理方式	影响
规则不命中	跳过，不调 LLM	无
LLM 调用失败	打 ERROR，不写日志	单条无分析
工具调用失败	打 ERROR，继续下一条	单条无工具结果
MQ 发送失败	打 WARN，MySQL 已写入	告警发出去了，但 MQ 没有
写日志失败	打 ERROR	单条无审计
关键原则：任何一环节失败都不影响主链路（MQTT → MySQL）。

七、测试
7.1 单元测试（RuleEngineTest）
5 个用例，执行时间：31ms 全过。

正常数据 → 不命中

温度 85-95 → TEMP_GT_85 WARNING

状态 == FAULT → 优先 STATE_FAULT

温度 > 95 → TEMP_GT_95 CRITICAL

振动 > 10 → VIB_GT_10 CRITICAL

7.2 端到端测试
方式：仿真层注入故障 → 观察 AI Agent 处理。
测试结果：注入 10 个故障，AI Agent 处理 8 台。
发现：CONVEYOR-01-01（振动缓慢上升）和 ASSEMBLY-01-01（瞬态）未触发规则。
认知：端到端测试依赖物理演化，时间长且不稳定，必须配合单元测试。

7.3 数据一致性对账
对账逻辑：

text
ai_analysis_log 总数 = WARNING + CRITICAL 总数
alert_record 总数 = CRITICAL 总数
实测数据：24 条分析（8 WARNING + 16 CRITICAL），16 条告警。差值 8 条 WARNING ✅。

八、演进路线
8.1 已完成（v1.6）
✅ 规则引擎 + LLM 双层架构

✅ Function Calling 工具（query_device_history / send_alert）

✅ LLM 客户端抽象（Mock 实现）

✅ Redis SETNX 去重

✅ @Async 异步 + 防御性拷贝

✅ 单元测试（5 个用例）

✅ 数据一致性对账

8.2 DeepSeek 接入（v1.7）
实现方式：

维度	实现
HTTP 客户端	Spring RestClient（原生，非框架）
协议	OpenAI Chat Completions 兼容格式
接口	POST https://api.deepseek.com/v1/chat/completions
鉴权	Authorization: Bearer ${DEEPSEEK_API_KEY}
模型	deepseek-chat
参数	temperature=0.3、stream=false
代码结构：

java
@Component
@ConditionalOnProperty(name = "ai.llm.provider", havingValue = "deepseek")
public class DeepSeekClient implements LlmClient {
    @Value("${ai.llm.deepseek.api-key}")
    private String apiKey;

    @Override
    public String chat(String systemPrompt, String userPrompt) {
        // 构造 OpenAI 兼容请求体
        // 发 HTTP POST
        // 解析 choices[0].message.content
    }
}
```