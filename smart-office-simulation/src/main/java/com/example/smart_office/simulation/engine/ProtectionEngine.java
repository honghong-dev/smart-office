package com.example.smart_office.simulation.engine;

import com.example.smart_office.simulation.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 保护域：熔断、降级、复位
 *
 * <p>工业硬逻辑，不走 AI。安全第一，毫秒级响应。
 *
 * <p>阈值：
 * <ul>
 *   <li>温度 > 95℃ → 降载</li>
 *   <li>温度 > 105℃ → 停机</li>
 *   <li>振动 > 10 mm/s → 停机</li>
 * </ul>
 */
@Component
public class ProtectionEngine {

    private static final Logger log = LoggerFactory.getLogger(ProtectionEngine.class);

    /** 温度降载阈值 */
    private static final double TEMP_LOAD_REDUCTION = 95.0;
    /** 温度停机阈值 */
    private static final double TEMP_SHUTDOWN = 105.0;
    /** 振动停机阈值 */
    private static final double VIB_SHUTDOWN = 10.0;

    /** 温度恢复阈值（低于此值可以复位） */
    private static final double TEMP_RECOVER = 50.0;
    /** 振动恢复阈值 */
    private static final double VIB_RECOVER = 3.0;

    /**
     * 检查并触发保护。
     *
     * @return 触发的保护动作列表（可能为空）
     */
    public List<ProtectionAction> check(Device device, long simTimeSec) {
        List<ProtectionAction> actions = new ArrayList<>();

        if (device.getState() == DeviceState.MAINTENANCE) return actions;
        if (device.getState() == DeviceState.FAULT) return actions;

        double temp = device.getCurrentTemperature();
        double vib = device.getCurrentVibration();

        // 温度停机
        if (temp > TEMP_SHUTDOWN) {
            actions.add(new ProtectionAction(
                    device.getDeviceId(), ProtectionType.SHUTDOWN,
                    "温度超过停机阈值", temp, TEMP_SHUTDOWN,
                    simTimeSec, System.currentTimeMillis()));
            device.transitionTo(DeviceState.FAULT);
            device.setLoadReduced(false);
            return actions;
        }

        // 振动停机
        if (vib > VIB_SHUTDOWN) {
            actions.add(new ProtectionAction(
                    device.getDeviceId(), ProtectionType.SHUTDOWN,
                    "振动超过停机阈值", vib, VIB_SHUTDOWN,
                    simTimeSec, System.currentTimeMillis()));
            device.transitionTo(DeviceState.FAULT);
            device.setLoadReduced(false);
            return actions;
        }

        // 降载（只触发一次）
        if (temp > TEMP_LOAD_REDUCTION
                && device.getState() == DeviceState.RUNNING
                && !device.isLoadReduced()) {
            actions.add(new ProtectionAction(
                    device.getDeviceId(), ProtectionType.LOAD_REDUCTION,
                    "温度超过降载阈值", temp, TEMP_LOAD_REDUCTION,
                    simTimeSec, System.currentTimeMillis()));
            device.setLoadReduced(true);
        }

        return actions;
    }

    /**
     * 复位：从 FAULT → MAINTENANCE → IDLE。
     *
     * @return 复位动作（成功返回 action，失败返回 null）
     */
    public ProtectionAction reset(Device device, long simTimeSec) {
        if (device.getState() != DeviceState.FAULT) {
            log.warn("设备 {} 状态不是 FAULT，无法复位", device.getDeviceId());
            return null;
        }

        // 必须满足恢复条件才能复位
        double temp = device.getCurrentTemperature();
        double vib = device.getCurrentVibration();
        if (temp > TEMP_RECOVER || vib > VIB_RECOVER) {
            log.warn("设备 {} 复位失败：温度 {}℃ > {}℃ 或 振动 {} > {}",
                    device.getDeviceId(), temp, TEMP_RECOVER, vib, VIB_RECOVER);
            return null;
        }

        // FAULT → MAINTENANCE → IDLE
        device.transitionTo(DeviceState.MAINTENANCE);
        device.transitionTo(DeviceState.IDLE);

        // 清除故障状态
        if (device.getFaultState() != null) {
            device.getFaultState().clear();
        }

        ProtectionAction action = new ProtectionAction(
                device.getDeviceId(), ProtectionType.RESET,
                "手动复位成功", 0, 0,
                simTimeSec, System.currentTimeMillis());

        log.info(">>> 设备 {} 复位成功", device.getDeviceId());
        return action;
    }
}