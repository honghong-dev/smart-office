package com.example.smart_office;

import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.util.concurrent.atomic.AtomicInteger;

public class MqttTestScript {
    public static void main(String[] args) throws Exception {

            String broker = "tcp://localhost:1884";
            String clientId = "test-script-" + System.currentTimeMillis();
            MqttClient client = new MqttClient(broker, clientId, new MemoryPersistence());

            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setUserName("admin");
            options.setPassword("admin123456".toCharArray());
            options.setConnectionTimeout(30);
            options.setKeepAliveInterval(60);
            client.connect(options);

            int total = 50000;
            int deviceCount = 100;

            AtomicInteger acked = new AtomicInteger(0);
            client.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    System.err.println("连接断开: " + cause.getMessage());
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                    acked.incrementAndGet();
                }
            });

            long baseTs = System.currentTimeMillis();   // 基准时间
            long start = System.currentTimeMillis();    // 发布计时开始

            for (int i = 0; i < total; i++) {
                String deviceId = String.format("%03d", i % deviceCount);
                String topic = "factory/device/" + deviceId + "/temperature";
                long ts = baseTs + i;   // 全局递增，不重复
                double temp = 70 + (i % 20);
                String payload = "{\"deviceId\":\"" + deviceId
                        + "\",\"temperature\":" + temp
                        + ",\"timestamp\":" + ts + "}";

                MqttMessage message = new MqttMessage(payload.getBytes());
                message.setQos(1);
                client.publish(topic, message);

                if (i % 1000 == 0 && i > 0) {
                    Thread.sleep(5);
                }
            }

            long publishEnd = System.currentTimeMillis();
            System.out.println("=== 发布完成 " + total + " 条，发布耗时: " + (publishEnd - start) + "ms，等待确认... ===");

            // ★ 等所有消息确认，最多等 120 秒
            long deadline = System.currentTimeMillis() + 120_000;
            while (acked.get() < total && System.currentTimeMillis() < deadline) {
                Thread.sleep(100);
            }

            long end = System.currentTimeMillis();
            System.out.println("=== 发送 " + total + " 条，已确认 " + acked.get()
                    + " 条，发布总耗时: " + (end - start) + "ms ===");
            System.out.println("=== 发布速率: " + (total * 1000L / (end - start)) + " 条/秒 ===");

            client.disconnect();   // ★ 补
            client.close();        // ★ 补
        }
}