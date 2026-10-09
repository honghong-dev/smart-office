package com.example.smart_office.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_approval_record")
public class SysApprovalRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long approvalId;
    private Long operatorId;
    private String action; // SUBMIT / APPROVE / REJECT
    private String comment;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}