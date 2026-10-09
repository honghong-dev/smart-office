package com.example.smart_office.simulation.engine;

import com.example.smart_office.simulation.domain.Device;
import com.example.smart_office.simulation.domain.DeviceRegistry;
import com.example.smart_office.simulation.domain.DeviceState;
import com.example.smart_office.simulation.domain.FaultState;
import com.example.smart_office.simulation.domain.FaultType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

@Component
public class FaultInjector {

    private static final Logger log = LoggerFactory.getLogger(FaultInjector.class);

    private final DeviceRegistry registry;

    public FaultInjector(DeviceRegistry registry) {
        this.registry = registry;
    }

    public void inject(String deviceId, FaultType type) {
        Device device = registry.get(deviceId);
        if (device == null) {
            log.warn("故障注入失败：设备不存在 {}", deviceId);
            return;
        }
        device.setFaultState(FaultState.inject(type));
        if (device.getState() == DeviceState.IDLE) {
            device.transitionTo(DeviceState.RUNNING);
            log.info("设备 {} 因故障注入，从待机切到运行", deviceId);
        }
        log.warn(">>> 故障注入：device={}, type={}, severity={}",
                deviceId, type.getDescription(), type.getSeverity().getDescription());
    }

    public int randomInject(double probability) {
        int count = 0;
        for (Device device : registry.getAll()) {
            if (device.getFaultState() != null && device.getFaultState().isActive()) {
                continue;
            }
            if (ThreadLocalRandom.current().nextDouble() < probability) {
                FaultType[] types = FaultType.values();
                FaultType chosen = types[ThreadLocalRandom.current().nextInt(types.length)];
                device.setFaultState(FaultState.inject(chosen));
                if (device.getState() == DeviceState.IDLE) {
                    device.transitionTo(DeviceState.RUNNING);
                }
                log.warn(">>> 随机故障注入：device={}, type={}", device.getDeviceId(), chosen.getDescription());
                count++;
            }
        }
        return count;
    }
}