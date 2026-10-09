package com.example.smart_office.simulation.config;

import com.example.smart_office.simulation.domain.Device;
import com.example.smart_office.simulation.domain.DeviceRegistry;
import com.example.smart_office.simulation.domain.DeviceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 设备初始化器（配置驱动，支持批量生成）
 */
@Component
public class DeviceSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DeviceSeeder.class);

    private final DeviceRegistry registry;
    private final DeviceGenerationProperties props;

    public DeviceSeeder(DeviceRegistry registry, DeviceGenerationProperties props) {
        this.registry = registry;
        this.props = props;
    }

    @Override
    public void run(String... args) {
        log.info("========== 开始初始化仿真设备 ==========");
        log.info("产线数 = {}, 每种设备数 = {}, 设备类型数 = {}",
                props.getLineCount(), props.getCountPerType(), props.getTypes().size());

        long start = System.currentTimeMillis();
        List<Device> devices = generateDevices();
        long elapsed = System.currentTimeMillis() - start;

        log.info("设备生成耗时：{} ms", elapsed);
        registry.registerAll(devices);
        log.info("========== 设备初始化完成，共 {} 台 ==========", registry.size());
    }

    /**
     * 批量生成设备：
     * 产线 LINE-01..LINE-N × 设备类型 × 每类 M 台
     * 命名：{TYPE}-{lineNum2位}-{seq2位}，如 PRESS-01-01
     */
    private List<Device> generateDevices() {
        List<Device> devices = new ArrayList<>();

        for (int lineNum = 1; lineNum <= props.getLineCount(); lineNum++) {
            String lineId = String.format("LINE-%02d", lineNum);

            for (DeviceType type : props.getTypes()) {
                for (int seq = 1; seq <= props.getCountPerType(); seq++) {
                    String deviceId = String.format("%s-%02d-%02d", type.name(), lineNum, seq);
                    String deviceName = String.format("%s-%02d-%02d", type.getDescription(), lineNum, seq);

                    devices.add(Device.builder()
                            .deviceId(deviceId)
                            .deviceName(deviceName)
                            .type(type)
                            .lineId(lineId)
                            .build());
                }
            }
        }
        return devices;
    }
}