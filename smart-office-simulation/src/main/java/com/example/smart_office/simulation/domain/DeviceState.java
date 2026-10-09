package com.example.smart_office.simulation.domain;

/**
 * 设备状态枚举
 *
 * <p>工业设备的四大基础状态，迁移规则见 {@link DeviceStateMachine}。
 *
 * @author 宏宏
 */
public enum DeviceState {

    /** 运行中：设备正常生产 */
    RUNNING("运行中"),

    /** 待机：设备通电但未生产（如待料、换班） */
    IDLE("待机"),

    /** 故障：设备异常，需要处理 */
    FAULT("故障"),

    /** 维修：设备故障后进入维修流程 */
    MAINTENANCE("维修中");

    private final String description;

    DeviceState(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}