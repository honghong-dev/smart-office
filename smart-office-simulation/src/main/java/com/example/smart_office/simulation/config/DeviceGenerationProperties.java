package com.example.smart_office.simulation.config;

import com.example.smart_office.simulation.domain.DeviceType;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 设备生成配置
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "simulation.device-generation")
public class DeviceGenerationProperties {

    /** 产线数量 */
    private int lineCount = 1;

    /** 每条产线每种设备类型的数量 */
    private int countPerType = 1;

    /** 设备类型列表 */
    private List<DeviceType> types = List.of(
            DeviceType.PRESS, DeviceType.WELD, DeviceType.PAINT,
            DeviceType.ASSEMBLY, DeviceType.CONVEYOR);
}