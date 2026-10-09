package com.example.smart_office.simulation.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 故障类型枚举
 */
@Getter
@AllArgsConstructor
public enum FaultType {

    /** 轴承磨损：振动缓慢上升 */
    BEARING_WEAR("轴承磨损", FaultSeverity.MEDIUM, 3600),

    /** 过热：温度指数上升 */
    OVERHEAT("过热", FaultSeverity.HIGH, 1800),

    /** 传感器漂移：数值缓慢偏移 */
    SENSOR_DRIFT("传感器漂移", FaultSeverity.LOW, 7200),

    /** 突发断裂：前 N 秒正常，之后跳变 */
    SUDDEN_FAILURE("突发断裂", FaultSeverity.CRITICAL, 60);

    private final String description;
    private final FaultSeverity severity;
    /** 典型演化时长（秒） */
    private final long evolveDurationSec;
}