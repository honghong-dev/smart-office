package com.example.smart_office.simulation.application;

import com.example.smart_office.simulation.domain.Device;
import com.example.smart_office.simulation.domain.DeviceRegistry;
import com.example.smart_office.simulation.domain.FaultType;
import com.example.smart_office.simulation.domain.ProtectionAction;
import com.example.smart_office.simulation.engine.FaultInjector;
import com.example.smart_office.simulation.engine.ProtectionEngine;
import org.springframework.stereotype.Service;

/**
 * 故障管理服务：对外暴露手动注入/复位接口
 *
 * <p>给"演示"和"Agent 触发"用。
 */
@Service
public class FaultManagementService {

    private final FaultInjector injector;
    private final ProtectionEngine protectionEngine;
    private final DeviceRegistry registry;

    public FaultManagementService(FaultInjector injector,
                                  ProtectionEngine protectionEngine,
                                  DeviceRegistry registry) {
        this.injector = injector;
        this.protectionEngine = protectionEngine;
        this.registry = registry;
    }

    /** 手动注入故障 */
    public void injectFault(String deviceId, FaultType type) {
        injector.inject(deviceId, type);
    }

    /** 随机批量注入（演示用） */
    public int randomInject(double probability) {
        return injector.randomInject(probability);
    }

    /** 手动复位 */
    public ProtectionAction reset(String deviceId) {
        Device device = registry.get(deviceId);
        if (device == null) return null;
        return protectionEngine.reset(device, System.currentTimeMillis() / 1000);
    }
}