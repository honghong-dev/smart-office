package com.example.smart_office.controller;

import com.example.smart_office.annotation.RequiresPermission;
import com.example.smart_office.common.Result;
import com.example.smart_office.entity.dto.ApprovalHandleDTO;
import com.example.smart_office.entity.dto.ApprovalSubmitDTO;
import com.example.smart_office.service.SysApprovalService;
import com.example.smart_office.utils.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/approval")
public class ApprovalController {

    @Autowired
    private SysApprovalService approvalService;

    @Autowired
    private JwtUtils jwtUtils;

    @PostMapping("/submit")
    @RequiresPermission("approval:submit")
    public Result<Long> submit(@RequestHeader("Authorization") String authHeader,
                               @RequestBody ApprovalSubmitDTO dto) {
        // 从 Token 中解析出申请人 ID
        String token = authHeader.substring(7);
        String username = jwtUtils.getUsernameFromToken(token);
        // 简化处理：假设申请人 ID 从 username 反查（你之前实现过 getUserIdByUsername）
        Long applicantId = 123L; // 临时写死，稍后替换成真实查询

        Long approvalId = approvalService.submitApproval(applicantId, dto);
        return Result.success(approvalId);
    }

    @PostMapping("/handle")
    @RequiresPermission("approval:handle")
    public Result<String> handleApproval(@RequestBody ApprovalHandleDTO dto) {
        approvalService.handleApproval(dto);
        return Result.success("处理成功");
    }
}