package com.example.smart_office.simulation.engine;

/**
 * 遥测采样值对象
 *
 * <p>设备一次 tick 产生的完整遥测数据。
 * <p>对应 MQTT payload 的结构，与主工程 device_telemetry 表字段对齐。
 */
public class TelemetrySample {

    private final String deviceId;
    private final String deviceName;
    private final String state;
    private final double temperature;
    private final double vibration;
    private final int speed;
    private final long timestamp;      // 仿真时间戳（毫秒）

    public TelemetrySample(String deviceId, String deviceName, String state,
                           double temperature, double vibration, int speed, long timestamp) {
        this.deviceId = deviceId;
        this.deviceName = deviceName;
        this.state = state;
        this.temperature = temperature;
        this.vibration = vibration;
        this.speed = speed;
        this.timestamp = timestamp;
    }

    public String getDeviceId() { return deviceId; }
    public String getDeviceName() { return deviceName; }
    public String getState() { return state; }
    public double getTemperature() { return temperature; }
    public double getVibration() { return vibration; }
    public int getSpeed() { return speed; }
    public long getTimestamp() { return timestamp; }
}