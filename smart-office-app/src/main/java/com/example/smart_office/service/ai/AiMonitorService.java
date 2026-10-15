package com.example.smart_office.service.ai;

import com.example.smart_office.entity.DeviceTelemetry;
import com.example.smart_office.entity.ai.AiAnalysisLog;
import com.example.smart_office.mapper.ai.AiAnalysisLogMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * AI 监控 Agent
 *
 * <p>挂载点：TelemetryBatchWriter.flush() 之后调用。
 * <p>职责：规则扫描 → 组装上下文 → 调 LLM → 执行工具 → 写日志
 */
@Service
public class AiMonitorService {

    private static final Logger log = LoggerFactory.getLogger(AiMonitorService.class);

    @Autowired
    private RuleEngine ruleEngine;

    @Autowired
    private LlmClient llmClient;

    @Autowired
    private AiToolExecutor toolExecutor;

    @Autowired
    private AiAnalysisLogMapper aiAnalysisLogMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

    /**
     * 处理一批遥测数据（异步，不阻塞主链路）
     */
    @Async
    public void onBatchFlushed(List<DeviceTelemetry> batch) {
        if (batch == null || batch.isEmpty()) return;

        List<DeviceTelemetry> snapshot = new ArrayList<>(batch);

        for (DeviceTelemetry t : snapshot) {
            try {
                Optional<RuleEngine.RuleHit> hit = ruleEngine.evaluate(t);
                if (hit.isEmpty()) continue;

                RuleEngine.RuleHit rule = hit.get();

                // ★ 去重：同一设备同一规则，5 分钟内只处理一次
                String dedupKey = "ai:agent:dedup:" + rule.getDeviceId() + ":" + rule.getRuleName();
                Boolean isFirst = redisTemplate.opsForValue()
                        .setIfAbsent(dedupKey, "1", Duration.ofMinutes(5));
                if (Boolean.FALSE.equals(isFirst)) {
                    // 5 分钟内已处理过，跳过
                    continue;
                }

                log.warn("[AI Agent] 规则命中: device={}, rule={}, value={}",
                        rule.getDeviceId(), rule.getRuleName(), rule.getTriggerValue());

                processHit(t, rule);
            } catch (Exception e) {
                log.error("[AI Agent] 处理异常: device={}", t.getDeviceId(), e);
            }
        }
    }

    /**
     * 单条异常处理：组装上下文 → 调 LLM → 执行工具 → 写日志
     */
    private void processHit(DeviceTelemetry t, RuleEngine.RuleHit rule) throws Exception {
        String deviceId = rule.getDeviceId();

        // 1. 查历史（供 LLM 研判）
        List<DeviceTelemetry> history = toolExecutor.queryDeviceHistory(deviceId, 10);

        // 2. 组装 prompt
        String systemPrompt = "你是工业设备监控专家，负责分析设备遥测异常。"
                + "请根据异常上下文，判断严重程度，并给出处置建议。";

        String userPrompt = String.format(
                "设备 %s 触发规则 %s（触发值=%s，级别=%s）。\n最近 10 条遥测：\n%s",
                deviceId, rule.getRuleName(), rule.getTriggerValue(), rule.getLevel(),
                objectMapper.writeValueAsString(history)
        );

        // 3. 调 LLM
        String llmResponse = llmClient.chat(systemPrompt, userPrompt);
        log.info("[AI Agent] LLM 研判结果: {}", llmResponse);

        // 4. 根据规则级别，自动发告警
        Long alertId = null;
        if ("CRITICAL".equals(rule.getLevel())) {
            alertId = toolExecutor.sendAlert(
                    deviceId,
                    rule.getLevel(),
                    "规则 " + rule.getRuleName() + " 命中（值=" + rule.getTriggerValue() + "）",
                    null
            );
        }

        // 5. 组装 tool_calls（合法 JSON）
        List<Map<String, Object>> toolCallsList = new ArrayList<>();
        if (alertId != null) {
            Map<String, Object> call = new HashMap<>();
            call.put("tool", "send_alert");
            call.put("alertId", alertId);
            toolCallsList.add(call);
        }
        String toolCallsJson = objectMapper.writeValueAsString(toolCallsList);

        // 6. 写 AI 分析日志
        AiAnalysisLog logEntity = new AiAnalysisLog();
        logEntity.setDeviceId(deviceId);
        logEntity.setTriggerRule(rule.getRuleName());
        logEntity.setTriggerValue(rule.getTriggerValue());
        logEntity.setContextJson(objectMapper.writeValueAsString(history));
        logEntity.setLlmResponse(llmResponse);
        logEntity.setToolCalls(toolCallsJson);
        logEntity.setAnalysisResult(rule.getLevel());
        logEntity.setCreatedAt(LocalDateTime.now());
        aiAnalysisLogMapper.insert(logEntity);

        log.info("[AI Agent] 分析日志已写入: id={}, device={}", logEntity.getId(), deviceId);
    }
}