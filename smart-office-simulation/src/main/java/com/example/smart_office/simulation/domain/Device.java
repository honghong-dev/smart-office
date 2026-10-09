package com.example.smart_office.simulation.domain;

import lombok.Getter;
import lombok.Setter;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 设备实体
 *
 * @author 宏宏
 */
@Getter
public class Device {

    private final String deviceId;
    private final String deviceName;
    private final DeviceType type;
    private final String lineId;
    private final double baseTemperature;
    private final double baseVibration;
    private final int ratedSpeed;

    @Setter
    private volatile DeviceState state;

    @Setter
    private volatile double currentTemperature;

    @Setter
    private volatile double currentVibration;

    @Setter
    private volatile int currentSpeed;

    @Setter
    private volatile boolean loadReduced = false;

    @Setter
    private volatile FaultState faultState;

    private final AtomicLong totalRuntimeSeconds = new AtomicLong(0);
    private volatile long stateEnterTime;

    private Device(Builder builder) {
        this.deviceId = builder.deviceId;
        this.deviceName = builder.deviceName;
        this.type = builder.type;
        this.lineId = builder.lineId;
        this.baseTemperature = builder.baseTemperature;
        this.baseVibration = builder.baseVibration;
        this.ratedSpeed = builder.ratedSpeed;

        // 初始状态：待机，温度/振动为基准值，转速 0
        this.state = DeviceState.IDLE;
        this.currentTemperature = builder.baseTemperature;
        this.currentVibration = builder.baseVibration;
        this.currentSpeed = 0;
        this.stateEnterTime = System.currentTimeMillis();
    }

    public synchronized void transitionTo(DeviceState newState) {
        DeviceStateMachine.validate(this.state, newState);
        this.state = newState;
        this.stateEnterTime = System.currentTimeMillis();
    }

    public void addRuntimeSeconds(long seconds) {
        if (this.state == DeviceState.RUNNING) {
            totalRuntimeSeconds.addAndGet(seconds);
        }
    }

    public long getTotalRuntimeSeconds() {
        return totalRuntimeSeconds.get();
    }

    @Override
    public String toString() {
        return String.format(
                "Device{id='%s', name='%s', type=%s, line='%s', state=%s(%.1f℃/%.2fmm/s/%drpm), runtime=%ds}",
                deviceId, deviceName, type.getDescription(), lineId,
                state.getDescription(), currentTemperature, currentVibration, currentSpeed,
                totalRuntimeSeconds.get()
        );
    }

    // ========== Builder（保留手写，因为有派生逻辑） ==========

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String deviceId;
        private String deviceName;
        private DeviceType type;
        private String lineId;
        private double baseTemperature;
        private double baseVibration;
        private int ratedSpeed;

        public Builder deviceId(String deviceId) {
            this.deviceId = deviceId;
            return this;
        }

        public Builder deviceName(String deviceName) {
            this.deviceName = deviceName;
            return this;
        }

        public Builder type(DeviceType type) {
            this.type = type;
            if (type != null) {
                this.baseTemperature = type.getBaseTemperature();
                this.baseVibration = type.getBaseVibration();
                this.ratedSpeed = type.getRatedSpeed();
            }
            return this;
        }

        public Builder lineId(String lineId) {
            this.lineId = lineId;
            return this;
        }

        public Builder baseTemperature(double baseTemperature) {
            this.baseTemperature = baseTemperature;
            return this;
        }

        public Builder baseVibration(double baseVibration) {
            this.baseVibration = baseVibration;
            return this;
        }

        public Builder ratedSpeed(int ratedSpeed) {
            this.ratedSpeed = ratedSpeed;
            return this;
        }

        public Device build() {
            return new Device(this);
        }
    }
}