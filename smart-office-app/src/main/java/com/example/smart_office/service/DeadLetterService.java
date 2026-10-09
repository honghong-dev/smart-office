package com.example.smart_office.service;

import com.example.smart_office.entity.MqttMessageLog;
import com.example.smart_office.mapper.MqttMessageLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class DeadLetterService {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterService.class);

    @Autowired
    private MqttMessageLogMapper messageLogMapper;

    public void record(String topic, String payload, String reason) {
        try {
            MqttMessageLog logEntry = new MqttMessageLog();
            // ★ 用 topic + nanoTime 保证唯一，避免主键冲突
            logEntry.setMessageId(topic + ":" + System.nanoTime());
            logEntry.setTopic(topic);
            logEntry.setPayload(payload);
            logEntry.setStatus("DEAD");
            logEntry.setReason(reason);
            messageLogMapper.insert(logEntry);
            log.warn("死信已持久化: topic={}, reason={}", topic, reason);   // ★ warn，不是 error
        } catch (Exception e) {
            // ★ 死信记录失败，绝不抛出去
            log.error("死信持久化失败，已忽略: {}", e.getMessage());
        }
    }
}