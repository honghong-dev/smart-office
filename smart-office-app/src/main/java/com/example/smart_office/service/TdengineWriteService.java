package com.example.smart_office.service;

import com.example.smart_office.entity.DeviceTelemetry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TdengineWriteService {

    @Value("${tdengine.url}")
    private String url;

    @Value("${tdengine.username:root}")
    private String username;

    @Value("${tdengine.password:taosdata}")
    private String password;

    private static final DateTimeFormatter TS_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private Connection conn;

    /** 已建子表缓存（避免重复 CREATE） */
    private final Set<String> createdSubTables = ConcurrentHashMap.newKeySet();

    @PostConstruct
    public void init() throws SQLException {
        log.info(">>> TDengine URL 实际值: {}", url);
        conn = DriverManager.getConnection(url, username, password);

        // ★ 自动建超表（幂等）
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(
                    "CREATE STABLE IF NOT EXISTS telemetry (" +
                            "  ts TIMESTAMP, " +
                            "  temperature DOUBLE" +
                            ") TAGS (" +
                            "  device_id NCHAR(32)" +
                            ")"
            );
        }
        log.info("TDengine 连接建立成功，超表 telemetry 已就绪: {}", url);
    }

    /**
     * 批量写入：先确保子表存在，再 INSERT
     */
    public void batchInsert(List<DeviceTelemetry> list) {
        if (list == null || list.isEmpty()) return;

        long start = System.currentTimeMillis();

        Map<String, List<DeviceTelemetry>> grouped = list.stream()
                .collect(Collectors.groupingBy(DeviceTelemetry::getDeviceId));

        // ① 先确保所有子表存在
        ensureSubTables(grouped.keySet());

        // ② 拼 INSERT（不带 USING，子表已建好）
        StringBuilder sql = new StringBuilder();
        boolean first = true;
        int written = 0;

        for (Map.Entry<String, List<DeviceTelemetry>> entry : grouped.entrySet()) {
            String deviceId = entry.getKey();
            List<DeviceTelemetry> rows = entry.getValue();
            String subTable = toSubTableName(deviceId);

            if (first) {
                sql.append("INSERT INTO ").append(subTable).append(" VALUES ");
                first = false;
            } else {
                sql.append(" ").append(subTable).append(" VALUES ");
            }

            for (int i = 0; i < rows.size(); i++) {
                DeviceTelemetry d = rows.get(i);
                if (i > 0) sql.append(",");
                sql.append("('")
                        .append(d.getReportTime().format(TS_FMT)).append("',")
                        .append(d.getTemperature())
                        .append(")");
            }
            written += rows.size();
        }

        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql.toString());
        } catch (SQLException e) {
            log.error("TDengine 写入失败", e);
            throw new RuntimeException("TDengine 写入失败", e);
        }

        long cost = System.currentTimeMillis() - start;
        log.info("TDengine 写入 {} 条（{} 设备），耗时 {}ms", written, grouped.size(), cost);
    }

    /**
     * 确保子表存在（幂等，只对未创建过的执行 CREATE）
     */
    private void ensureSubTables(Set<String> deviceIds) {
        for (String deviceId : deviceIds) {
            String subTable = toSubTableName(deviceId);
            if (createdSubTables.contains(subTable)) continue;

            String createSql = String.format(
                    "CREATE TABLE IF NOT EXISTS %s USING telemetry TAGS ('%s')",
                    subTable, escape(deviceId));
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate(createSql);
                createdSubTables.add(subTable);
            } catch (SQLException e) {
                log.error("TDengine 建子表失败: {}", createSql, e);
                throw new RuntimeException("建子表失败", e);
            }
        }
    }

    private String toSubTableName(String deviceId) {
        return "d_" + deviceId.replaceAll("[^a-zA-Z0-9_]", "_");
    }

    private String escape(String s) {
        return s == null ? "" : s.replace("'", "''");
    }

    @PreDestroy
    public void close() {
        try {
            if (conn != null && !conn.isClosed()) {
                conn.close();
                log.info("TDengine 连接已关闭");
            }
        } catch (SQLException e) {
            log.warn("TDengine 连接关闭异常", e);
        }
    }
}