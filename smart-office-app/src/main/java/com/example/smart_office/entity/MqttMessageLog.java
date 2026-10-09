package com.example.smart_office.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mqtt_message_log")
public class MqttMessageLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String messageId;
    private String topic;
    private String payload;
    private String status;   // RECEIVED / PROCESSED / FAILED / DEAD
    private String reason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}