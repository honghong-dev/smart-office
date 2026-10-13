package com.example.smart_office.service.ai;

import com.example.smart_office.entity.DeviceTelemetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 规则引擎：从一批遥测数据中筛选"疑似异常"
 *
 * <p>设计原则：毫秒级过滤 99% 正常数据，只把异常片段交给 LLM 研判。
 */
@Component
public class RuleEngine {

    private static final Logger log = LoggerFactory.getLogger(RuleEngine.class);

    // 阈值配置（对齐仿真层保护域）
    private static final double TEMP_CRITICAL = 95.0;
    private static final double TEMP_WARNING = 85.0;
    private static final double VIB_CRITICAL = 10.0;

    /**
     * 规则匹配结果
     */
    public static class RuleHit {
        private final String deviceId;
        private final String ruleName;      // 如 TEMP_GT_95
        private final String triggerValue;  // 如 "96.3"
        private final String level;         // WARNING / CRITICAL

        public RuleHit(String deviceId, String ruleName, String triggerValue, String level) {
            this.deviceId = deviceId;
            this.ruleName = ruleName;
            this.triggerValue = triggerValue;
            this.level = level;
        }

        public String getDeviceId() { return deviceId; }
        public String getRuleName() { return ruleName; }
        public String getTriggerValue() { return triggerValue; }
        public String getLevel() { return level; }
    }

    /**
     * 对单条遥测数据做规则匹配。
     *
     * @return 命中时返回 RuleHit，未命中返回 Optional.empty()
     */
    public Optional<RuleHit> evaluate(DeviceTelemetry t) {
        if (t == null || t.getDeviceId() == null) {
            return Optional.empty();
        }

        String deviceId = t.getDeviceId();

        // 规则 1：状态 == FAULT → CRITICAL
        if ("FAULT".equalsIgnoreCase(t.getState())) {
            return Optional.of(new RuleHit(deviceId, "STATE_FAULT", "FAULT", "CRITICAL"));
        }

        // 规则 2：温度 > 95 → CRITICAL
        if (t.getTemperature() != null && t.getTemperature() > TEMP_CRITICAL) {
            return Optional.of(new RuleHit(deviceId, "TEMP_GT_95",
                    String.format("%.2f", t.getTemperature()), "CRITICAL"));
        }

        // 规则 3：振动 > 10 → CRITICAL
        if (t.getVibration() != null && t.getVibration() > VIB_CRITICAL) {
            return Optional.of(new RuleHit(deviceId, "VIB_GT_10",
                    String.format("%.2f", t.getVibration()), "CRITICAL"));
        }

        // 规则 4：温度 > 85 → WARNING
        if (t.getTemperature() != null && t.getTemperature() > TEMP_WARNING) {
            return Optional.of(new RuleHit(deviceId, "TEMP_GT_85",
                    String.format("%.2f", t.getTemperature()), "WARNING"));
        }

        return Optional.empty();
    }
}