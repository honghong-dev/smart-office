package com.example.smart_office.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("device_telemetry")
public class DeviceTelemetry {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String deviceId;
    private Double temperature;
    private Double vibration;   // ★ 新增
    private Integer speed;       // ★ 新增
    private String state;        // ★ 新增
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime reportTime;
}