package com.example.smart_office.simulation.engine;

import com.example.smart_office.simulation.domain.Device;
import com.example.smart_office.simulation.domain.DeviceRegistry;
import com.example.smart_office.simulation.domain.ProtectionAction;
import com.example.smart_office.simulation.infrastructure.mqtt.TelemetryPublisher;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 仿真调度器
 */
@Component
public class SimulationScheduler {

    private static final Logger log = LoggerFactory.getLogger(SimulationScheduler.class);

    private final DeviceRegistry registry;
    private final TelemetrySimulator simulator;
    private final TelemetryPublisher publisher;
    private final ProtectionEngine protectionEngine;
    private final FaultInjector faultInjector;

    @Value("${simulation.tick-interval-ms:1000}")
    private long tickIntervalMs;

    @Value("${simulation.time-scale:1}")
    private int timeScale;

    private ScheduledExecutorService scheduler;
    private final AtomicLong simTimeSec = new AtomicLong(0);

    public SimulationScheduler(DeviceRegistry registry,
                               TelemetrySimulator simulator,
                               TelemetryPublisher publisher,
                               ProtectionEngine protectionEngine,
                               FaultInjector faultInjector) {
        this.registry = registry;
        this.simulator = simulator;
        this.publisher = publisher;
        this.protectionEngine = protectionEngine;
        this.faultInjector = faultInjector;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        log.info("========== 仿真调度器启动 ==========");
        log.info("tick 间隔 = {}ms, 时间倍率 = {}x", tickIntervalMs, timeScale);
        log.info("设备数量 = {}", registry.size());

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "simulation-scheduler");
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleAtFixedRate(this::tick,
                tickIntervalMs, tickIntervalMs, TimeUnit.MILLISECONDS);
    }

    private void tick() {
        long currentSimTime = simTimeSec.addAndGet(timeScale);
        int successCount = 0;
        int errorCount = 0;
        int protectionCount = 0;

        // 自动故障注入：每 120 仿真秒触发一次
        if (currentSimTime % 120 == 0 && currentSimTime > 0) {
            int injected = faultInjector.randomInject(0.005);
            if (injected > 0) {
                log.warn(">>> 自动故障注入：{} 台", injected);
            }
        }

        for (Device device : registry.getAll()) {
            try {
                // ① 生成遥测（含故障演化）
                com.example.smart_office.simulation.engine.TelemetrySample sample =
                        simulator.simulate(device, currentSimTime, timeScale);

                // ② 保护检查
                List<ProtectionAction> actions = protectionEngine.check(device, currentSimTime);
                for (ProtectionAction action : actions) {
                    protectionCount++;
                    log.warn("保护动作：{}", action);
                }

                // ③ 发送 MQTT
                publisher.publish(sample);
                successCount++;
            } catch (Exception e) {
                errorCount++;
                log.error("设备 {} 仿真失败", device.getDeviceId(), e);
            }
        }

        log.debug("tick #{} (仿真时间={}s)：成功 {}，失败 {}，保护 {}",
                currentSimTime / timeScale, currentSimTime, successCount, errorCount, protectionCount);
    }

    @PreDestroy
    public void shutdown() {
        log.info("========== 仿真调度器关闭 ==========");
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}