package com.example.smart_office.service.ai;

import com.example.smart_office.entity.DeviceTelemetry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class RuleEngineTest {

    private final RuleEngine ruleEngine = new RuleEngine();

    @Test
    @DisplayName("温度 > 95 → 命中 TEMP_GT_95 CRITICAL")
    void testTemperatureCritical() {
        DeviceTelemetry t = new DeviceTelemetry();
        t.setDeviceId("TEST-01");
        t.setTemperature(96.0);
        t.setState("RUNNING");
        t.setVibration(1.0);

        Optional<RuleEngine.RuleHit> hit = ruleEngine.evaluate(t);
        assertTrue(hit.isPresent());
        assertEquals("TEMP_GT_95", hit.get().getRuleName());
        assertEquals("CRITICAL", hit.get().getLevel());
    }

    @Test
    @DisplayName("温度 85-95 → 命中 TEMP_GT_85 WARNING")
    void testTemperatureWarning() {
        DeviceTelemetry t = new DeviceTelemetry();
        t.setDeviceId("TEST-01");
        t.setTemperature(88.0);
        t.setState("RUNNING");
        t.setVibration(1.0);

        Optional<RuleEngine.RuleHit> hit = ruleEngine.evaluate(t);
        assertTrue(hit.isPresent());
        assertEquals("TEMP_GT_85", hit.get().getRuleName());
        assertEquals("WARNING", hit.get().getLevel());
    }

    @Test
    @DisplayName("振动 > 10 → 命中 VIB_GT_10 CRITICAL")
    void testVibrationCritical() {
        DeviceTelemetry t = new DeviceTelemetry();
        t.setDeviceId("TEST-01");
        t.setTemperature(70.0);
        t.setState("RUNNING");
        t.setVibration(11.0);

        Optional<RuleEngine.RuleHit> hit = ruleEngine.evaluate(t);
        assertTrue(hit.isPresent());
        assertEquals("VIB_GT_10", hit.get().getRuleName());
    }

    @Test
    @DisplayName("状态 == FAULT → 优先级最高，命中 STATE_FAULT")
    void testStateFaultPriority() {
        DeviceTelemetry t = new DeviceTelemetry();
        t.setDeviceId("TEST-01");
        t.setTemperature(120.0);   // 温度也超阈值
        t.setState("FAULT");        // 状态也 FAULT
        t.setVibration(15.0);

        Optional<RuleEngine.RuleHit> hit = ruleEngine.evaluate(t);
        assertTrue(hit.isPresent());
        // 验证优先级：FAULT 优先
        assertEquals("STATE_FAULT", hit.get().getRuleName());
    }

    @Test
    @DisplayName("正常数据 → 不命中")
    void testNormalData() {
        DeviceTelemetry t = new DeviceTelemetry();
        t.setDeviceId("TEST-01");
        t.setTemperature(60.0);
        t.setState("RUNNING");
        t.setVibration(2.0);

        Optional<RuleEngine.RuleHit> hit = ruleEngine.evaluate(t);
        assertFalse(hit.isPresent());
    }
}