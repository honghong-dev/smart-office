//写审计日志
package com.example.smart_office.service;

import com.example.smart_office.entity.SysAuditLog;
import com.example.smart_office.mapper.SysAuditLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    @Autowired
    private SysAuditLogMapper auditLogMapper;

    @Autowired
    private HttpServletRequest request;

    /**
     * 记录审计日志
     */
    public void record(Long operatorId, String operatorName, String operation,
                       Long targetId, String targetType, String oldValue, String newValue) {
        try {
            SysAuditLog auditLog = new SysAuditLog();
            auditLog.setOperatorId(operatorId);
            auditLog.setOperatorName(operatorName);
            auditLog.setOperation(operation);
            auditLog.setTargetId(targetId);
            auditLog.setTargetType(targetType);
            auditLog.setOldValue(oldValue);
            auditLog.setNewValue(newValue);
            auditLog.setIp(getClientIp());
            auditLogMapper.insert(auditLog);
            log.info("审计日志已记录: operator={}, operation={}, target={}",
                    operatorName, operation, targetId);
        } catch (Exception e) {
            log.error("审计日志记录失败", e);
        }
    }

    /**
     * 获取客户端 IP
     */
    private String getClientIp() {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}