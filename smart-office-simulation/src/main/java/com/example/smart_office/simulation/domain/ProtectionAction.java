package com.example.smart_office.simulation.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ProtectionAction {

    private final String deviceId;
    private final ProtectionType actionType;
    private final String reason;
    private final double triggerValue;
    private final double threshold;
    private final long simTimeSec;
    private final long realTimestamp;

    @Override
    public String toString() {
        return String.format("[%s] device=%s, reason=%s, value=%.2f, threshold=%.2f, simTime=%ds",
                actionType.getDescription(), deviceId, reason, triggerValue, threshold, simTimeSec);
    }
}