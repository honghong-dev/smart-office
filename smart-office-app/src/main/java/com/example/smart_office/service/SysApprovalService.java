//提交审批、处理审批
package com.example.smart_office.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.smart_office.entity.SysApproval;
import com.example.smart_office.entity.dto.ApprovalHandleDTO;
import com.example.smart_office.entity.dto.ApprovalSubmitDTO;

public interface SysApprovalService extends IService<SysApproval> {
    /**
     * 提交审批
     * @param applicantId 申请人ID（从 Token 中解析）
     * @param dto 审批参数
     * @return 审批单ID
     */
    Long submitApproval(Long applicantId, ApprovalSubmitDTO dto);

    void handleApproval(ApprovalHandleDTO dto);

    class RuleEngineTest {
    }
}