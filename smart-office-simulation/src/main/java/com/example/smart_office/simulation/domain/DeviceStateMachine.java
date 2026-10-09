package com.example.smart_office.simulation.domain;

import java.util.Map;
import java.util.Set;

/**
 * 设备状态机
 *
 * <p>定义合法状态迁移规则，非法迁移抛异常。
 *
 * <p>工业级设计意图：防止状态被"跳步"修改。
 * 例如 FAULT 不能直接到 RUNNING，必须先经过 MAINTENANCE（维修）。
 * 这是工业安全底线——故障设备必须维修后才能重新运行。
 *
 * @author 宏宏
 */
public final class DeviceStateMachine {

    /**
     * 状态迁移表：key = 当前状态，value = 允许迁移到的目标状态集合
     */
    private static final Map<DeviceState, Set<DeviceState>> TRANSITIONS = Map.of(
            DeviceState.RUNNING, Set.of(
                    DeviceState.IDLE,        // 运行 → 待机（正常切换）
                    DeviceState.FAULT        // 运行 → 故障（异常发生）
            ),
            DeviceState.IDLE, Set.of(
                    DeviceState.RUNNING,     // 待机 → 运行（恢复生产）
                    DeviceState.FAULT        // 待机 → 故障（待机也能故障）
            ),
            DeviceState.FAULT, Set.of(
                    DeviceState.MAINTENANCE  // 故障 → 维修（必须经维修，不能直接回 RUNNING）
            ),
            DeviceState.MAINTENANCE, Set.of(
                    DeviceState.IDLE,        // 维修完 → 待机（先待机确认）
                    DeviceState.RUNNING      // 维修完 → 运行（直接恢复生产）
            )
    );

    private DeviceStateMachine() {
        // 工具类，禁止实例化
    }

    /**
     * 校验状态迁移是否合法。
     *
     * @param from 当前状态
     * @param to   目标状态
     * @throws IllegalStateException 非法迁移时抛出，附带清晰错误信息
     */
    public static void validate(DeviceState from, DeviceState to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("状态不能为 null：from=" + from + ", to=" + to);
        }
        if (from == to) {
            // 同状态迁移视为幂等操作，允许（不报错，不改变状态）
            return;
        }
        Set<DeviceState> allowed = TRANSITIONS.get(from);
        if (allowed == null || !allowed.contains(to)) {
            throw new IllegalStateException(String.format(
                    "非法状态迁移：%s(%s) → %s(%s)，允许的目标状态：%s",
                    from, from.getDescription(),
                    to, to.getDescription(),
                    allowed
            ));
        }
    }

    /**
     * 判断迁移是否合法（不抛异常版本，供业务判断用）
     */
    public static boolean canTransition(DeviceState from, DeviceState to) {
        if (from == null || to == null) return false;
        if (from == to) return true;
        Set<DeviceState> allowed = TRANSITIONS.get(from);
        return allowed != null && allowed.contains(to);
    }
}