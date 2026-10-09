package com.example.smart_office.entity.dto;

import lombok.Data;

@Data
public class ApprovalSubmitDTO {
    private String title;        // 审批标题
    private String type;         // 审批类型（请假/报销/出差）
    private String content;      // 审批内容
    private Long approverId;     // 审批人ID
}