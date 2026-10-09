package com.example.smart_office.simulation.infrastructure.mqtt;

import com.example.smart_office.simulation.engine.TelemetrySample;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 遥测数据 MQTT 发送器
 *
 * <p>负责：
 * <ul>
 *   <li>连接 EMQX（启动时）</li>
 *   <li>将 TelemetrySample 序列化为 JSON，发送到 MQTT</li>
 *   <li>优雅关闭连接</li>
 * </ul>
 *
 * <p>Topic 规则：factory/device/{deviceId}/telemetry
 */
@Component
public class TelemetryPublisher {

    private static final Logger log = LoggerFactory.getLogger(TelemetryPublisher.class);

    private final MqttProperties props;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private MqttClient client;

    public TelemetryPublisher(MqttProperties props) {
        this.props = props;
    }

    @PostConstruct
    public void init() throws MqttException {
        // 每次启动用唯一 clientId，避免和上次冲突
        String clientId = props.getClientId() + "-" + UUID.randomUUID().toString().substring(0, 8);

        client = new MqttClient(props.getBrokerUrl(), clientId, new MemoryPersistence());

        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(true);                 // 发送端不需要持久会话
        options.setAutomaticReconnect(true);           // 自动重连
        options.setConnectionTimeout(10);
        options.setKeepAliveInterval(30);
        if (props.getUsername() != null && !props.getUsername().isEmpty()) {
            options.setUserName(props.getUsername());
            options.setPassword(props.getPassword().toCharArray());
        }

        client.connect(options);
        log.info("MQTT 发送端已连接：broker={}, clientId={}", props.getBrokerUrl(), clientId);
    }

    /**
     * 发送一次遥测数据。
     *
     * @param sample 遥测采样
     */
    public void publish(TelemetrySample sample) {
        try {
            String topic = props.getTopicTemplate()
                    .replace("{deviceId}", sample.getDeviceId());

            String payload = objectMapper.writeValueAsString(sample);

            MqttMessage message = new MqttMessage(payload.getBytes());
            message.setQos(props.getQos());
            message.setRetained(false);

            client.publish(topic, message);
            log.debug("已发送：topic={}, payload={}", topic, payload);
        } catch (Exception e) {
            log.error("MQTT 发送失败：deviceId={}", sample.getDeviceId(), e);
        }
    }

    @PreDestroy
    public void shutdown() {
        if (client != null && client.isConnected()) {
            try {
                client.disconnect();
                client.close();
                log.info("MQTT 发送端已关闭");
            } catch (MqttException e) {
                log.error("关闭 MQTT 连接异常", e);
            }
        }
    }
}