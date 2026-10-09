package com.example.smart_office.entity.dto;

import lombok.Data;

@Data
public class ApprovalHandleDTO {
    private Long approvalId;
    private Integer status;   // 1 通过，2 驳回
    private String comment;
}