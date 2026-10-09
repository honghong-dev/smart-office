package com.example.smart_office.simulation.engine;

import com.example.smart_office.simulation.domain.Device;
import com.example.smart_office.simulation.domain.FaultState;
import com.example.smart_office.simulation.domain.FaultType;
import org.springframework.stereotype.Component;
import com.example.smart_office.simulation.domain.DeviceState;

/**
 * 故障演化引擎
 * 把故障状态反映到设备物理量上，返回绝对增量。
 */
@Component
public class FaultEvolver {

    public FaultEffect evolve(Device device, long deltaSec) {
        FaultState fs = device.getFaultState();
        if (fs == null || !fs.isActive()) {
            return FaultEffect.none();
        }

        // 设备已 FAULT 或 MAINTENANCE，故障不再演化
        if (device.getState() == DeviceState.FAULT
                || device.getState() == DeviceState.MAINTENANCE) {
            return FaultEffect.none();
        }

        fs.advance(deltaSec);
        FaultType type = fs.getType();
        double progress = fs.getProgress();
        long elapsed = fs.getElapsedSec();

        double tempDelta = 0;
        double vibDelta = 0;

        switch (type) {
            case OVERHEAT:
                // 每 5 秒 +5℃，50 秒 +50℃（触发停机）
                tempDelta = 5.0 * (elapsed / 5.0);
                break;

            case BEARING_WEAR:
                // 振动峰值 +15 mm/s（基准 2 + 15 = 17 > 10 阈值）
                vibDelta = progress * 15.0;
                break;

            case SENSOR_DRIFT:
                // 温度峰值 +8℃
                tempDelta = progress * 8.0;
                break;

            case SUDDEN_FAILURE:
                // 前 30 秒正常，之后跳变
                if (elapsed > 30) {
                    tempDelta = 50.0;
                    vibDelta = 12.0;
                }
                break;
        }

        return new FaultEffect(tempDelta, vibDelta, false);
    }

    public static class FaultEffect {
        public final double tempDelta;
        public final double vibDelta;
        public final boolean triggered;

        public FaultEffect(double tempDelta, double vibDelta, boolean triggered) {
            this.tempDelta = tempDelta;
            this.vibDelta = vibDelta;
            this.triggered = triggered;
        }

        public static FaultEffect none() {
            return new FaultEffect(0, 0, false);
        }
    }
}