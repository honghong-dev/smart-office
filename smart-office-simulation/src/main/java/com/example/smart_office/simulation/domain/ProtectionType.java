package com.example.smart_office.simulation.domain;

/**
 * 保护动作类型
 */
public enum ProtectionType {

    /** 降载：速度降为 50% */
    LOAD_REDUCTION("降载"),

    /** 停机：设备进入 FAULT 状态 */
    SHUTDOWN("停机"),

    /** 复位：从 FAULT 恢复到 IDLE */
    RESET("复位"),

    /** 维修：进入 MAINTENANCE */
    MAINTENANCE("维修");

    private final String description;

    ProtectionType(String description) {
        this.description = description;
    }

    public String getDescription() { return description; }
}