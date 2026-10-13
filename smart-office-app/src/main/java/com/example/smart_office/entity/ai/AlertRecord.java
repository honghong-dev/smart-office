package com.example.smart_office.entity.ai;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("alert_record")
public class AlertRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String deviceId;
    private String level;
    private String reason;
    private String source;
    private Long aiLogId;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime handledAt;
    private String handledBy;
}