//订阅 MQTT 消息，入队
package com.example.smart_office.service;

import jakarta.annotation.PostConstruct;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class MqttSubscriber implements MqttCallbackExtended {

    private static final Logger log = LoggerFactory.getLogger(MqttSubscriber.class);
//    private static final String TOPIC = "factory/device/+/temperature";
    private static final String TOPIC = "factory/device/+/telemetry";

    @Autowired
    private MqttClient mqttClient;

    @Autowired
    private TelemetryQueue telemetryQueue;

    @PostConstruct
    public void init() throws Exception {
        mqttClient.setCallback(this);
        log.info(">>> MQTT clientId 实际值: {}", mqttClient.getClientId());   // ★ 加这行
        if (mqttClient.isConnected()) {
            subscribeTopic();
        }
    }

    private void subscribeTopic() throws Exception {
        mqttClient.subscribe(TOPIC, 1);   // QoS 1
        log.info("已订阅主题: {}，QoS=1", TOPIC);
    }

    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        log.info("MQTT 连接完成，reconnect={}, serverURI={}", reconnect, serverURI);
        try {
            subscribeTopic();   // 每次重连后重新订阅，保证持久会话生效
        } catch (Exception e) {
            log.error("订阅失败，将在下次重连时重试", e);
        }
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        String payload = new String(message.getPayload());
//        log.info("收到 MQTT 消息: topic={}, payload={}", topic, payload);
        telemetryQueue.offer(topic, payload);
    }

    @Override
    public void connectionLost(Throwable cause) {
        log.warn("MQTT 连接丢失，等待自动重连", cause);
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // 订阅端不需要处理
    }

}