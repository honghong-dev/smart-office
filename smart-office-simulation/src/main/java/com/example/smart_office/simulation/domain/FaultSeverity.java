package com.example.smart_office.simulation.domain;

/**
 * 故障严重度
 */
public enum FaultSeverity {
    LOW(1, "低"),
    MEDIUM(2, "中"),
    HIGH(3, "高"),
    CRITICAL(4, "紧急");

    private final int level;
    private final String description;

    FaultSeverity(int level, String description) {
        this.level = level;
        this.description = description;
    }

    public int getLevel() { return level; }
    public String getDescription() { return description; }
}