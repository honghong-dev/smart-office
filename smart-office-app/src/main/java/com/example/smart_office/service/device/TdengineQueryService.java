package com.example.smart_office.service.device;

import com.example.smart_office.entity.DeviceTelemetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * TDengine 查询服务
 *
 * <p>用 JDBC 直查 TDengine（REST 模式），不经过 MyBatis-Plus。
 * <p>职责：只查"热数据"（近 7 天）。
 */
@Service
public class TdengineQueryService {

    private static final Logger log = LoggerFactory.getLogger(TdengineQueryService.class);
    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    @Value("${tdengine.url}")
    private String url;

    @Value("${tdengine.username:root}")
    private String username;

    @Value("${tdengine.password:taosdata}")
    private String password;

    /**
     * 按设备 + 时间范围查 TDengine
     *
     * @param deviceId 设备 ID
     * @param startTime 起始时间（含）
     * @param endTime 截止时间（含）
     * @param limit 最大条数
     * @return 遥测数据列表（按时间升序）
     */
    public List<DeviceTelemetry> query(String deviceId, LocalDateTime startTime, LocalDateTime endTime, int limit) {
        String sql = String.format(
                "SELECT ts, temperature FROM telemetry " +
                        "WHERE device_id = '%s' AND ts >= '%s' AND ts <= '%s' " +
                        "ORDER BY ts ASC LIMIT %d",
                deviceId,
                startTime.format(TS_FORMAT),
                endTime.format(TS_FORMAT),
                limit
        );

        List<DeviceTelemetry> result = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(url, username, password);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                DeviceTelemetry t = new DeviceTelemetry();
                t.setDeviceId(deviceId);
                t.setReportTime(rs.getTimestamp("ts").toLocalDateTime());
                t.setTemperature(rs.getDouble("temperature"));
                result.add(t);
            }
            log.info("[TDengine] 查询完成: device={}, 时间范围={} ~ {}, 返回 {} 条",
                    deviceId, startTime, endTime, result.size());
            return result;

        } catch (Exception e) {
            log.error("[TDengine] 查询失败: device={}", deviceId, e);
            // 降级：返回空列表，不抛异常（避免影响主链路）
            return new ArrayList<>();
        }
    }
}