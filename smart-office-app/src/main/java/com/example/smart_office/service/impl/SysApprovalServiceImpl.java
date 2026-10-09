//提交审批、处理审批
package com.example.smart_office.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.smart_office.config.RabbitMQConfig;
import com.example.smart_office.entity.SysApproval;
import com.example.smart_office.entity.SysApprovalRecord;
import com.example.smart_office.entity.dto.ApprovalHandleDTO;
import com.example.smart_office.entity.dto.ApprovalSubmitDTO;
import com.example.smart_office.mapper.SysApprovalMapper;
import com.example.smart_office.mapper.SysApprovalRecordMapper;
import com.example.smart_office.service.AuditLogService;
import com.example.smart_office.service.SysApprovalService;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronization;

import java.util.concurrent.TimeUnit;

@Service
public class SysApprovalServiceImpl extends ServiceImpl<SysApprovalMapper, SysApproval> implements SysApprovalService {

    @Autowired
    private SysApprovalRecordMapper recordMapper;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RedissonClient redissonClient;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submitApproval(Long applicantId, ApprovalSubmitDTO dto) {
        // 1. 保存审批单
        SysApproval approval = new SysApproval();
        approval.setTitle(dto.getTitle());
        approval.setType(dto.getType());
        approval.setContent(dto.getContent());
        approval.setApplicantId(applicantId);
        approval.setApproverId(dto.getApproverId());
        approval.setStatus(0); // 待审批
        this.save(approval);

        // 2. 插入提交记录
        SysApprovalRecord record = new SysApprovalRecord();
        record.setApprovalId(approval.getId());
        record.setOperatorId(applicantId);
        record.setAction("SUBMIT");
        record.setComment("发起审批");
        recordMapper.insert(record);

        // 3. 发送延迟消息（超时提醒）
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.APPROVAL_DELAY_EXCHANGE,
                RabbitMQConfig.APPROVAL_DELAY_ROUTING_KEY,
                approval.getId()
        );

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        rabbitTemplate.convertAndSend(
                                RabbitMQConfig.APPROVAL_DELAY_EXCHANGE,
                                RabbitMQConfig.APPROVAL_DELAY_ROUTING_KEY,
                                approval.getId()
                        );
                    }
                }
        );

        return approval.getId();
    }

    @Autowired
    private AuditLogService auditLogService;

    public void handleApproval(ApprovalHandleDTO dto) {
        Long approvalId = dto.getApprovalId();
        RLock lock = redissonClient.getLock("approval:lock:" + approvalId);
        try {
            // 尝试加锁，最多等 5 秒，锁 10 秒自动释放
            boolean locked = lock.tryLock(5, 10, TimeUnit.SECONDS);
            if (!locked) {
                throw new RuntimeException("审批单正在处理中，请稍后重试");
            }
            Thread.sleep(5000);   // 模拟业务处理 5 秒
            // 业务逻辑
            SysApproval approval = this.getById(approvalId);
            if (approval == null) {
                throw new RuntimeException("审批单不存在");
            }
            if (approval.getStatus() != 0) {
                throw new RuntimeException("审批单已处理");
            }
            approval.setStatus(dto.getStatus());
            this.updateById(approval);

            // 插入审批记录
            SysApprovalRecord record = new SysApprovalRecord();
            record.setApprovalId(approvalId);
            record.setOperatorId(1L);   // 从 Token 里取
            record.setAction(dto.getStatus() == 1 ? "APPROVE" : "REJECT");
            record.setComment(dto.getComment());
            recordMapper.insert(record);

            // 记录审计日志
            auditLogService.record(
                    1L,   // 操作人 ID，从 Token 取
                    "approver",   // 操作人用户名，从 Token 取
                    "HANDLE_APPROVAL",
                    approvalId,
                    "APPROVAL",
                    "0",
                    dto.getStatus().toString()
            );

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("加锁失败");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

}