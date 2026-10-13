package com.example.smart_office.entity.ai;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_analysis_log")
public class AiAnalysisLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String deviceId;
    private String triggerRule;
    private String triggerValue;
    private String contextJson;
    private String llmResponse;
    private String toolCalls;
    private String analysisResult;
    private LocalDateTime createdAt;
}