package com.example.smart_office.service;

import com.example.smart_office.entity.DeviceTelemetry;
import com.example.smart_office.mapper.DeviceTelemetryMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 批量写入设备数据
 * 职责：MQTT 消费、攒批、双写调度（MySQL 主链路 + TDengine 旁路）
 */
@Component
public class TelemetryBatchWriter {

    private static final Logger log = LoggerFactory.getLogger(TelemetryBatchWriter.class);

    /** 批量大小：攒够 1000 条触发 flush */
    private static final int BATCH_SIZE = 1000;

    /** 超时兜底：距上次 flush 超过 1 秒，强制 flush */
    private static final long FLUSH_INTERVAL_MS = 1000;

    /** 消费线程数 */
    private static final int CONSUMER_THREADS = 4;

    /** 队列空了之后，至少等 200ms 再 flush，避免单条立刻插入 */
    private static final long QUEUE_EMPTY_FLUSH_DELAY_MS = 200;

    @Autowired
    private TelemetryQueue telemetryQueue;

    @Autowired
    private DeviceTelemetryMapper telemetryMapper;

    @Autowired
    private MqttIdempotentService idempotentService;

    @Autowired
    private DeadLetterService deadLetterService;

    @Autowired
    private TdengineWriteService tdengineWriteService;

    @Autowired
    private com.example.smart_office.service.ai.AiMonitorService aiMonitorService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ExecutorService executor;
    private volatile boolean running = true;

    // 压测统计
    private final AtomicLong mysqlTotalMs = new AtomicLong(0);
    private final AtomicLong tdTotalMs = new AtomicLong(0);
    private final AtomicLong totalCount = new AtomicLong(0);
    private final AtomicLong mysqlBatchCount = new AtomicLong(0);
    private final AtomicLong tdBatchCount = new AtomicLong(0);

    @PostConstruct
    public void start() {
        executor = Executors.newFixedThreadPool(CONSUMER_THREADS, r -> {
            Thread t = new Thread(r, "telemetry-batch-writer-" + System.nanoTime());
            t.setDaemon(true);
            return t;
        });

        for (int i = 0; i < CONSUMER_THREADS; i++) {
            executor.submit(this::consumeLoop);
        }

        log.info("TelemetryBatchWriter 已启动，batchSize={}, flushInterval={}ms, threads={}",
                BATCH_SIZE, FLUSH_INTERVAL_MS, CONSUMER_THREADS);
    }

    /**
     * 消费循环：每个线程独立 batch + lastFlush
     */
    private void consumeLoop() {
        List<DeviceTelemetry> batch = new ArrayList<>(BATCH_SIZE);
        long lastFlush = System.currentTimeMillis();

        while (running) {
            try {
                TelemetryQueue.TelemetryMessage msg = telemetryQueue.take();
                DeviceTelemetry telemetry = parseAndCheckIdempotent(msg);
                if (telemetry != null) {
                    batch.add(telemetry);
                }

                long now = System.currentTimeMillis();
                boolean queueEmpty = telemetryQueue.size() == 0;

                boolean shouldFlush = batch.size() >= BATCH_SIZE
                        || now - lastFlush >= FLUSH_INTERVAL_MS
                        || (queueEmpty && !batch.isEmpty()
                        && now - lastFlush >= QUEUE_EMPTY_FLUSH_DELAY_MS);

                if (shouldFlush) {
                    flush(batch);
                    batch.clear();
                    lastFlush = now;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("批量消费异常", e);
            }
        }

        if (!batch.isEmpty()) {
            flush(batch);
        }
    }

    /**
     * 解析 + 幂等校验。返回 null 表示消息被跳过（重复或解析失败）。
     */
    private DeviceTelemetry parseAndCheckIdempotent(TelemetryQueue.TelemetryMessage msg) {
        try {
            JsonNode node = objectMapper.readTree(msg.payload());
            log.info("★ 原始 payload: {}", msg.payload());
            String deviceId = node.get("deviceId").asText();
            double temperature = node.get("temperature").asDouble();
            Double vibration = node.has("vibration") ? node.get("vibration").asDouble() : null;
            Integer speed = node.has("speed") ? node.get("speed").asInt() : null;
            String state = node.has("state") ? node.get("state").asText() : null;

            long ts = node.has("timestamp")
                    ? node.get("timestamp").asLong()
                    : System.currentTimeMillis();

            String messageId = deviceId + ":" + ts;
            if (!idempotentService.tryAcquire(messageId)) {
                log.debug("重复消息，已跳过: {}", messageId);
                return null;
            }

            DeviceTelemetry telemetry = new DeviceTelemetry();
            telemetry.setDeviceId(deviceId);
            telemetry.setTemperature(temperature);
            telemetry.setVibration(vibration);
            telemetry.setSpeed(speed);
            telemetry.setState(state);
            telemetry.setReportTime(java.time.LocalDateTime.now());
            return telemetry;
        } catch (Exception e) {
            log.error("消息解析失败，进入死信: {}", msg.payload(), e);
            deadLetterService.record(msg.topic(), msg.payload(), "PARSE_ERROR");
            return null;
        }
    }

    /**
     * 批量插入。MySQL 主链路，TDengine 旁路。
     */
    private void flush(List<DeviceTelemetry> batch) {
        if (batch.isEmpty()) {
            return;
        }

        // ========== 主链路：MySQL 写入 ==========
        long mysqlStart = System.currentTimeMillis();
        try {
            telemetryMapper.insertBatch(batch);
            long mysqlCost = System.currentTimeMillis() - mysqlStart;
            mysqlTotalMs.addAndGet(mysqlCost);
            mysqlBatchCount.incrementAndGet();

            int alertCount = 0;
            for (DeviceTelemetry t : batch) {
                if (t.getTemperature() > 80) {
                    alertCount++;
                }
            }
            if (alertCount > 0) {
                log.warn("本批 {} 条中，{} 条超过 80℃", batch.size(), alertCount);
            }
        } catch (Exception e) {
            log.error("MySQL 批量插入失败，整批进入死信", e);
            for (DeviceTelemetry t : batch) {
                deadLetterService.record("factory/device/telemetry",
                        t.getDeviceId() + ":" + t.getTemperature(), "DB_ERROR");
            }
            return;
        }


        // ========== ★★★ 新增：AI 监控 Agent（异步，不阻塞主链路）★★★ ==========
        try {
            aiMonitorService.onBatchFlushed(batch);
        } catch (Exception e) {
            log.error("AI Agent 调用失败，不影响主链路", e);
        }

        // ========== 旁路：TDengine 双写 ==========
        long tdStart = System.currentTimeMillis();
        try {
            tdengineWriteService.batchInsert(batch);
            long tdCost = System.currentTimeMillis() - tdStart;
            tdTotalMs.addAndGet(tdCost);
            tdBatchCount.incrementAndGet();
        } catch (Exception e) {
            log.error("TDengine 双写失败，已记死信，不影响 MySQL 主链路", e);
            for (DeviceTelemetry t : batch) {
                deadLetterService.record("factory/device/telemetry",
                        t.getDeviceId() + ":" + t.getTemperature(), "TDENGINE_ERROR");
            }
        }

        // ========== 累计统计：每 1 万条打一次汇总 ==========
        long total = totalCount.addAndGet(batch.size());
        log.info(">>> 累计：{}", total);   // ★ 加这行，每批都打
        if (total / 10000 > (total - batch.size()) / 10000) {
            long mBatches = mysqlBatchCount.get();
            long tBatches = tdBatchCount.get();
            String mAvg = String.format("%.2f",
                    mBatches == 0 ? 0 : (double) mysqlTotalMs.get() / mBatches);
            String tAvg = String.format("%.2f",
                    tBatches == 0 ? 0 : (double) tdTotalMs.get() / tBatches);

            log.info("===== 压测累计 {} 条 =====", total);
            log.info("MySQL    : 总 {}ms | 批次 {} | 均 {}ms/批",
                    mysqlTotalMs.get(), mBatches, mAvg);
            log.info("TDengine : 总 {}ms | 批次 {} | 均 {}ms/批",
                    tdTotalMs.get(), tBatches, tAvg);
        }
    }

    /**
     * 优雅关闭：停止消费线程，等待最多 10 秒，flush 剩余。
     */
    @PreDestroy
    public void stop() {
        running = false;
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                executor.shutdownNow();
            }
        }
    }
}