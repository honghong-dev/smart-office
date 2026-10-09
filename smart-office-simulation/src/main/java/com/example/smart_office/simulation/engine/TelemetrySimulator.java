package com.example.smart_office.simulation.engine;

import com.example.smart_office.simulation.domain.Device;
import com.example.smart_office.simulation.domain.DeviceState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 遥测仿真引擎
 *
 * <p>核心职责：为每台设备生成符合物理规律的遥测数据。
 *
 * <p>物理模型（温度）：
 * <pre>
 *   temperature = baseTemperature
 *               + cycleAmplitude × sin(2π × simTime / 86400s)  // 昼夜周期 24h
 *               + wearTrend × simTime                          // 劣化趋势
 *               + gaussianNoise(0, 0.3)                        // 测量噪声
 * </pre>
 *
 * <p>振动模型：
 * <pre>
 *   vibration = baseVibration
 *             + 0.1 × (currentTemperature - baseTemperature)  // 温度耦合
 *             + gaussianNoise(0, 0.1)
 * </pre>
 *
 * <p>状态影响：
 * <ul>
 *   <li>RUNNING：温度/振动正常演化，转速 = 额定值</li>
 *   <li>IDLE：温度向基准回归，振动衰减，转速 = 0</li>
 *   <li>FAULT / MAINTENANCE：本批暂按 IDLE 处理（第三批做故障演化）</li>
 * </ul>
 */
@Component
public class TelemetrySimulator {

    private static final Logger log = LoggerFactory.getLogger(TelemetrySimulator.class);

    /** 每台设备的"劣化累积量"，key = deviceId */
    private final ConcurrentHashMap<String, Double> wearTrends = new ConcurrentHashMap<>();

    /** 温度噪声标准差（℃） */
    private static final double TEMP_NOISE_STDDEV = 0.3;

    /** 振动噪声标准差（mm/s） */
    private static final double VIB_NOISE_STDDEV = 0.1;

    /** 昼夜周期振幅（℃） */
    private static final double CYCLE_AMPLITUDE = 3.0;

    /** 劣化趋势速率（℃/秒，累积非常慢） */
    private static final double WEAR_RATE_PER_SEC = 0.0001;

    private final FaultEvolver faultEvolver;

    public TelemetrySimulator(FaultEvolver faultEvolver) {
        this.faultEvolver = faultEvolver;
    }
    /**
     * 生成一次遥测采样。
     *
     * @param device     设备
     * @param simTimeSec 当前仿真时间（秒），从仿真启动累计
     * @param deltaSec   本次 tick 推进的仿真秒数
     */
    public TelemetrySample simulate(Device device, long simTimeSec, long deltaSec) {

        String deviceId = device.getDeviceId();
        DeviceState state = device.getState();
        double baseTemp = device.getBaseTemperature();
        double baseVib = device.getBaseVibration();

        double temperature;
        double vibration;
        int speed;

        // ★ 新：先计算故障影响
        FaultEvolver.FaultEffect effect = faultEvolver.evolve(device, deltaSec);

        if (state == DeviceState.RUNNING) {

            double wear = wearTrends.merge(deviceId,
                    WEAR_RATE_PER_SEC * deltaSec,
                    Double::sum);

            double cycle = CYCLE_AMPLITUDE * Math.sin(2 * Math.PI * simTimeSec / 86400.0);

            temperature = baseTemp + cycle + wear + gaussian(TEMP_NOISE_STDDEV) + effect.tempDelta;
            vibration = baseVib + 0.1 * (temperature - baseTemp) + gaussian(VIB_NOISE_STDDEV) + effect.vibDelta;
            speed = (int) (device.getRatedSpeed() * (1.0 + gaussian(0.02)));

        } else if (state == DeviceState.IDLE) {
            double curTemp = device.getCurrentTemperature();
            double curVib = device.getCurrentVibration();

            temperature = baseTemp + (curTemp - baseTemp) * 0.9 + gaussian(TEMP_NOISE_STDDEV * 0.5) + effect.tempDelta * 0.5;
            vibration = baseVib + (curVib - baseVib) * 0.85 + gaussian(VIB_NOISE_STDDEV * 0.5) + effect.vibDelta * 0.5;
            speed = 0;

        } else {
            // FAULT / MAINTENANCE：温度振动冻结（只保留微小噪声）
            temperature = device.getCurrentTemperature() + gaussian(TEMP_NOISE_STDDEV * 0.3);
            vibration = device.getCurrentVibration() + gaussian(VIB_NOISE_STDDEV * 0.3);
            speed = 0;
        }

        device.setCurrentTemperature(temperature);
        device.setCurrentVibration(vibration);
        device.setCurrentSpeed(speed);
        device.addRuntimeSeconds(deltaSec);

        long simTimestamp = System.currentTimeMillis()
                + (simTimeSec - (System.currentTimeMillis() / 1000)) * 1000;

        return new TelemetrySample(
                deviceId, device.getDeviceName(), state.name(),
                round2(temperature), round2(vibration), speed, simTimestamp
        );
    }

    /**
     * 高斯噪声（均值 0，标准差 stddev）
     */
    private double gaussian(double stddev) {
        return ThreadLocalRandom.current().nextGaussian() * stddev;
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}