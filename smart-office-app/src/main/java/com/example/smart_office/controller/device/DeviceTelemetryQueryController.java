package com.example.smart_office.controller.device;

import com.example.smart_office.common.Result;
import com.example.smart_office.entity.DeviceTelemetry;
import com.example.smart_office.service.device.DeviceTelemetryQueryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 设备遥测查询接口（冷热分离）
 */
@RestController
@RequestMapping("/api/device")
public class DeviceTelemetryQueryController {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private DeviceTelemetryQueryService queryService;

    /**
     * 按设备 + 时间范围查询遥测数据
     *
     * <p>参数：
     * <ul>
     *   <li>deviceId：设备 ID（必填）</li>
     *   <li>startTime：起始时间（yyyy-MM-dd HH:mm:ss，必填）</li>
     *   <li>endTime：截止时间（yyyy-MM-dd HH:mm:ss，必填）</li>
     *   <li>limit：最大条数（默认 100）</li>
     * </ul>
     */
    @GetMapping("/telemetry")
    public Result<List<DeviceTelemetry>> queryTelemetry(
            @RequestParam String deviceId,
            @RequestParam String startTime,
            @RequestParam String endTime,
            @RequestParam(defaultValue = "100") int limit) {

        LocalDateTime start = LocalDateTime.parse(startTime, FORMATTER);
        LocalDateTime end = LocalDateTime.parse(endTime, FORMATTER);

        List<DeviceTelemetry> data = queryService.query(deviceId, start, end, limit);
        return Result.success(data);
    }
}