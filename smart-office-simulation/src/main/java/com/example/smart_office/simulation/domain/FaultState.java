package com.example.smart_office.simulation.domain;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FaultState {

    private FaultType type;
    /** 故障已持续的仿真秒数 */
    private long elapsedSec;
    /** 故障是否仍活跃 */
    private boolean active;

    public static FaultState none() {
        return new FaultState();
    }

    public static FaultState inject(FaultType type) {
        FaultState fs = new FaultState();
        fs.type = type;
        fs.elapsedSec = 0;
        fs.active = true;
        return fs;
    }

    public void advance(long deltaSec) {
        if (active) {
            this.elapsedSec += deltaSec;
        }
    }

    /** 故障演化进度：0.0 ~ 1.0（到 1.0 就是峰值） */
    public double getProgress() {
        if (type == null) return 0.0;
        return Math.min(1.0, (double) elapsedSec / type.getEvolveDurationSec());
    }

    public void clear() {
        this.type = null;
        this.elapsedSec = 0;
        this.active = false;
    }
}