//配置 EMQX 连接、QoS、持久会话

package com.example.smart_office.config;

import jakarta.annotation.PreDestroy;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@RefreshScope
@Configuration
public class MqttConfig {

    @Value("${mqtt.broker}")
    private String broker;

    @Value("${mqtt.client-id}")
    private String clientId;

    @Value("${mqtt.username}")
    private String username;

    @Value("${mqtt.password}")
    private String password;

    private MqttClient mqttClient;

    @Bean
    public MqttClient mqttClient() throws Exception {
        mqttClient = new MqttClient(broker, clientId, new MemoryPersistence());

        MqttConnectOptions options = new MqttConnectOptions();
        options.setUserName(username);
        options.setPassword(password.toCharArray());
//        options.setCleanSession(true);
        options.setCleanSession(false);          // ★ 持久会话
        options.setAutomaticReconnect(true);     // ★ 自动重连（打开）

        options.setConnectionTimeout(30);
        options.setKeepAliveInterval(60);
        options.setMaxInflight(1000);   // 加这行
        mqttClient.connect(options);
        return mqttClient;
    }

    @PreDestroy
    public void destroy() {
        if (mqttClient != null && mqttClient.isConnected()) {
            try {
                mqttClient.disconnect();
                mqttClient.close();
                System.out.println("旧 MQTT 连接已断开");
            } catch (Exception e) {
                System.err.println("断开 MQTT 连接失败: " + e.getMessage());
            }
        }
    }
}