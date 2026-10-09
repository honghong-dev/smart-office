package com.example.smart_office.simulation.infrastructure.mqtt;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MQTT 配置绑定
 *
 * <p>对应 application.yml 里的 mqtt.* 配置项。
 */
@ConfigurationProperties(prefix = "mqtt")
public class MqttProperties {

    private String brokerUrl;
    private String clientId;
    private String username;
    private String password;
    private int qos = 1;
    private String topicTemplate;

    public String getBrokerUrl() { return brokerUrl; }
    public void setBrokerUrl(String brokerUrl) { this.brokerUrl = brokerUrl; }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public int getQos() { return qos; }
    public void setQos(int qos) { this.qos = qos; }

    public String getTopicTemplate() { return topicTemplate; }
    public void setTopicTemplate(String topicTemplate) { this.topicTemplate = topicTemplate; }
}