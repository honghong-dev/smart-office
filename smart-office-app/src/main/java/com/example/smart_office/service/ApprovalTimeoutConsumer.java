//消费死信队列，超时提醒
package com.example.smart_office.service;

import com.example.smart_office.entity.SysApproval;
import com.example.smart_office.mapper.SysApprovalMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ApprovalTimeoutConsumer {

    private static final Logger log = LoggerFactory.getLogger(ApprovalTimeoutConsumer.class);

    @Autowired
    private SysApprovalMapper approvalMapper;

    @Autowired
    private MqttIdempotentService idempotentService;

    @RabbitListener(queues = "approval.dlx.queue")
    public void handleTimeout(Long approvalId) {
        log.info("收到审批超时消息: approvalId={}", approvalId);

        // 幂等：同一个 approvalId，只处理一次
        String key = "approval:timeout:" + approvalId;
        if (!idempotentService.tryAcquire(key)) {
            log.info("审批超时消息已处理，跳过: approvalId={}", approvalId);
            return;
        }

        SysApproval approval = approvalMapper.selectById(approvalId);
        if (approval == null) {
            log.warn("审批单不存在: approvalId={}", approvalId);
            return;
        }

        // 状态 0 = 待审批，其他状态 = 已处理
        if (approval.getStatus() != 0) {
            log.info("审批单已处理，跳过提醒: approvalId={}, status={}", approvalId, approval.getStatus());
            return;
        }

        // 未处理，发提醒
        log.warn("⚠️ 审批超时提醒: approvalId={}, title={}, approverId={}",
                approvalId, approval.getTitle(), approval.getApproverId());

        // TODO: 后续接入钉钉/邮件/站内信
    }
}