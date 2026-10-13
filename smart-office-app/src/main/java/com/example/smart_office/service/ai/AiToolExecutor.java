package com.example.smart_office.service.ai;

import com.example.smart_office.config.RabbitMQConfig;
import com.example.smart_office.entity.DeviceTelemetry;
import com.example.smart_office.entity.ai.AlertRecord;
import com.example.smart_office.mapper.DeviceTelemetryMapper;
import com.example.smart_office.mapper.ai.AlertRecordMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI Agent 工具执行器
 *
 * <p>LLM 通过 Function Calling 请求调用以下工具：
 * <ul>
 *   <li>query_device_history(deviceId, limit) —— 查设备历史数据</li>
 *   <li>send_alert(deviceId, level, reason) —— 发送告警（写表 + 发 MQ）</li>
 * </ul>
 */
@Component
public class AiToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(AiToolExecutor.class);

    @Autowired
    private DeviceTelemetryMapper telemetryMapper;

    @Autowired
    private AlertRecordMapper alertRecordMapper;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    /**
     * 工具 1：查设备历史（最近 N 条）
     */
    public List<DeviceTelemetry> queryDeviceHistory(String deviceId, int limit) {
        log.info("[Tool] query_device_history(deviceId={}, limit={})", deviceId, limit);
        LambdaQueryWrapper<DeviceTelemetry> qw = new LambdaQueryWrapper<>();
        qw.eq(DeviceTelemetry::getDeviceId, deviceId)
                .orderByDesc(DeviceTelemetry::getReportTime)
                .last("LIMIT " + limit);
        return telemetryMapper.selectList(qw);
    }

    /**
     * 工具 2：发送告警（写 MySQL + 发 RabbitMQ）
     */
    @Autowired
    private ObjectMapper objectMapper;
    public Long sendAlert(String deviceId, String level, String reason, Long aiLogId) {
        log.info("[Tool] send_alert(deviceId={}, level={}, reason={})", deviceId, level, reason);

        // 1. 写 MySQL
        AlertRecord record = new AlertRecord();
        record.setDeviceId(deviceId);
        record.setLevel(level);
        record.setReason(reason);
        record.setSource("AI_AGENT");
        record.setAiLogId(aiLogId);
        record.setStatus(0);
        record.setCreatedAt(LocalDateTime.now());
        alertRecordMapper.insert(record);

        // 2. 发 RabbitMQ（异步，后续可扩钉钉/邮件/短信）
        try {
            String json = objectMapper.writeValueAsString(record);
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.AI_ALERT_EXCHANGE,
                    RabbitMQConfig.AI_ALERT_ROUTING_KEY,
                    json
            );
            log.info("[Tool] 告警已写入 MySQL(id={}) 并发送到 MQ", record.getId());
        } catch (Exception e) {
            log.warn("[Tool] 告警发送 MQ 失败，但 MySQL 已写入: id={}", record.getId(), e);
        }

        return record.getId();
    }

}