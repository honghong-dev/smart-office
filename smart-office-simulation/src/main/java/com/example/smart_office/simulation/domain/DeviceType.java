package com.example.smart_office.simulation.domain;

/**
 * 设备类型枚举
 *
 * <p>按长城汽车冲焊车间工艺链路设计：冲压 → 焊接 → 涂装 → 总装。
 * <p>每种设备有其典型的物理参数范围（供遥测仿真引擎使用）。
 *
 * @author 宏宏
 */
public enum DeviceType {

    /** 冲压机：高振动、中温 */
    PRESS("冲压机", 60.0, 2.0, 1200),

    /** 焊机：高温、中振动 */
    WELD("焊机", 75.0, 1.5, 800),

    /** 涂装设备：中温、低振动 */
    PAINT("涂装设备", 55.0, 0.8, 500),

    /** 总装设备：常温、低振动 */
    ASSEMBLY("总装设备", 45.0, 0.5, 300),

    /** 传送带：常温、中振动 */
    CONVEYOR("传送带", 40.0, 1.8, 200);

    private final String description;
    /** 基准温度（额定工况下的典型温度，单位 ℃） */
    private final double baseTemperature;
    /** 基准振动（额定工况下的典型振动，单位 mm/s） */
    private final double baseVibration;
    /** 额定转速（单位 rpm） */
    private final int ratedSpeed;

    DeviceType(String description, double baseTemperature, double baseVibration, int ratedSpeed) {
        this.description = description;
        this.baseTemperature = baseTemperature;
        this.baseVibration = baseVibration;
        this.ratedSpeed = ratedSpeed;
    }

    public String getDescription() {
        return description;
    }

    public double getBaseTemperature() {
        return baseTemperature;
    }

    public double getBaseVibration() {
        return baseVibration;
    }

    public int getRatedSpeed() {
        return ratedSpeed;
    }
}