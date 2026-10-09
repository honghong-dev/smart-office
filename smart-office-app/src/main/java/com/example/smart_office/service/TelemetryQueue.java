package com.example.smart_office.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;



@Component
public class TelemetryQueue {

    @Autowired
    private DeadLetterService deadLetterService;   // ← 加这行

    private static final Logger log = LoggerFactory.getLogger(TelemetryQueue.class);
    private static final int QUEUE_CAPACITY = 100000;

    private final BlockingQueue<TelemetryMessage> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);

    public void offer(String topic, String payload) {
        TelemetryMessage msg = new TelemetryMessage(topic, payload);
        if (!queue.offer(msg)) {
            // 队列满：落死信，不阻塞 MQTT 回调线程
            log.error("遥测队列已满，消息进入死信: topic={}, payload={}", topic, payload);
            deadLetterService.record(topic, payload, "QUEUE_FULL");
        }
    }

    public TelemetryMessage take() throws InterruptedException {
        return queue.take();
    }

    public int size() {
        return queue.size();
    }

    public record TelemetryMessage(String topic, String payload) {}
}