package com.example.smart_office.service.device;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.smart_office.entity.DeviceTelemetry;
import com.example.smart_office.mapper.DeviceTelemetryMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 设备遥测查询服务（冷热分离）
 *
 * <p>策略：
 * <ul>
 *   <li>热数据（近 7 天）→ 查 TDengine（快、时序优化）</li>
 *   <li>冷数据（7 天前）→ 查 MySQL（全量、事务）</li>
 *   <li>跨边界 → 两边查，合并结果</li>
 * </ul>
 */
@Service
public class DeviceTelemetryQueryService {

    private static final Logger log = LoggerFactory.getLogger(DeviceTelemetryQueryService.class);

    @Value("${device.telemetry.hot-data-days:7}")
    private int hotDataDays;

    /** 热数据阈值：近 7 天 */
    private static final int HOT_DATA_DAYS = 7;

    @Autowired
    private TdengineQueryService tdengineQueryService;

    @Autowired
    private DeviceTelemetryMapper telemetryMapper;

    /**
     * 按设备 + 时间范围查询遥测数据（自动冷热路由）
     */
    /**
     * 按设备 + 时间范围查询遥测数据（自动冷热路由）
     */
    public List<DeviceTelemetry> query(String deviceId, LocalDateTime startTime, LocalDateTime endTime, int limit) {
        LocalDateTime hotBoundary = LocalDateTime.now().minusDays(HOT_DATA_DAYS);

        // 场景 1：全部在热数据区（近 7 天）
        if (startTime.isAfter(hotBoundary)) {
            log.info("[Query] 全部热数据，走 TDengine: device={}", deviceId);
            return tdengineQueryService.query(deviceId, startTime, endTime, limit);
        }

        // 场景 2：全部在冷数据区（7 天前）
        if (endTime.isBefore(hotBoundary)) {
            log.info("[Query] 全部冷数据，走 MySQL: device={}", deviceId);
            return queryMysql(deviceId, startTime, endTime, limit);
        }

        // 场景 3：跨边界（部分冷、部分热）
        log.info("[Query] 跨边界，两边查后合并: device={}", deviceId);

        List<DeviceTelemetry> coldPart = queryMysql(deviceId, startTime, hotBoundary, limit);
        List<DeviceTelemetry> hotPart = tdengineQueryService.query(deviceId, hotBoundary, endTime, limit);

        // 合并（冷 + 热）
        List<DeviceTelemetry> merged = new ArrayList<>();
        merged.addAll(coldPart);
        merged.addAll(hotPart);

        // 按时间排序
        merged.sort((a, b) -> a.getReportTime().compareTo(b.getReportTime()));

        // 截断到 limit
        if (merged.size() > limit) {
            merged = merged.subList(0, limit);
        }

        // ★ 加这行日志，证明冷热分离在工作
        log.info("[Query] 合并完成: 冷数据 {} 条 + 热数据 {} 条 = 总计 {} 条",
                coldPart.size(), hotPart.size(), merged.size());

        return merged;
    }

    /**
     * 查 MySQL（冷数据）
     */
    private List<DeviceTelemetry> queryMysql(String deviceId, LocalDateTime startTime, LocalDateTime endTime, int limit) {
        LambdaQueryWrapper<DeviceTelemetry> qw = new LambdaQueryWrapper<>();
        qw.eq(DeviceTelemetry::getDeviceId, deviceId)
                .ge(DeviceTelemetry::getReportTime, startTime)
                .le(DeviceTelemetry::getReportTime, endTime)
                .orderByAsc(DeviceTelemetry::getReportTime)
                .last("LIMIT " + limit);
        return telemetryMapper.selectList(qw);
    }
}