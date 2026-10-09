package com.example.smart_office.simulation.domain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 设备注册表
 *
 * <p>内存中管理所有仿真设备，线程安全。
 * <p>职责：
 * <ul>
 *   <li>设备注册/注销</li>
 *   <li>按 ID / 类型 / 产线查询</li>
 *   <li>提供全量遍历（供调度器 tick）</li>
 * </ul>
 *
 * @author 宏宏
 */
@Component
public class DeviceRegistry {

    private static final Logger log = LoggerFactory.getLogger(DeviceRegistry.class);

    /** 设备存储：deviceId → Device，ConcurrentHashMap 保证线程安全 */
    private final Map<String, Device> devices = new ConcurrentHashMap<>();

    /**
     * 注册一台设备。
     *
     * @param device 设备
     * @throws IllegalStateException 设备ID重复时抛出
     */
    public void register(Device device) {
        Device existing = devices.putIfAbsent(device.getDeviceId(), device);
        if (existing != null) {
            throw new IllegalStateException("设备ID重复：" + device.getDeviceId());
        }
        log.info("设备已注册：{}", device);
    }

    /**
     * 批量注册。
     */
    public void registerAll(Collection<Device> deviceList) {
        deviceList.forEach(this::register);
        log.info("批量注册完成，当前设备总数：{}", devices.size());
    }

    /**
     * 按 ID 查设备。
     *
     * @return 设备，不存在返回 null
     */
    public Device get(String deviceId) {
        return devices.get(deviceId);
    }

    /**
     * 查某类型的所有设备。
     */
    public List<Device> getByType(DeviceType type) {
        return devices.values().stream()
                .filter(d -> d.getType() == type)
                .collect(Collectors.toList());
    }

    /**
     * 查某产线的所有设备。
     */
    public List<Device> getByLine(String lineId) {
        return devices.values().stream()
                .filter(d -> lineId.equals(d.getLineId()))
                .collect(Collectors.toList());
    }

    /**
     * 查某状态的所有设备。
     */
    public List<Device> getByState(DeviceState state) {
        return devices.values().stream()
                .filter(d -> d.getState() == state)
                .collect(Collectors.toList());
    }

    /**
     * 返回全量设备的只读视图（供调度器 tick 遍历）。
     *
     * <p>返回 Collection 而非 List，避免调用方误用下标；
     * 用 unmodifiableCollection 包装，防止外部修改注册表。
     */
    public Collection<Device> getAll() {
        return Collections.unmodifiableCollection(devices.values());
    }

    /**
     * 当前设备总数。
     */
    public int size() {
        return devices.size();
    }

    /**
     * 清空（测试用）。
     */
    public void clear() {
        devices.clear();
    }
}