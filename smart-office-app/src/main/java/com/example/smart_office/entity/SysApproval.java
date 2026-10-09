package com.example.smart_office.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_approval")
public class SysApproval {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String type;
    private String content;
    private Long applicantId;
    private Long approverId;
    private Integer status; // 0待审批 1已通过 2已驳回
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}