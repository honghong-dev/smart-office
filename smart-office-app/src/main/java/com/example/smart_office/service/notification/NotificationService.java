package com.example.smart_office.service.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 站内信通知服务
 *
 * <p>职责：通过 WebSocket 推送给前端。
 * <p>订阅地址：/topic/alerts（广播给所有订阅者）
 * <p>点对点：/queue/user/{userId}（推给特定用户）
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    /**
     * 广播告警（所有人都能收到）
     */
    public void broadcastAlert(String title, String content, String level) {
        Map<String, Object> payload = Map.of(
                "title", title,
                "content", content,
                "level", level,
                "timestamp", LocalDateTime.now().toString()
        );

        messagingTemplate.convertAndSend("/topic/alerts", payload);
        log.info("[WebSocket] 广播告警: title={}, level={}", title, level);
    }

    /**
     * 点对点通知（指定用户收到）
     */
    public void sendToUser(String userId, String title, String content) {
        Map<String, Object> payload = Map.of(
                "title", title,
                "content", content,
                "timestamp", LocalDateTime.now().toString()
        );

        messagingTemplate.convertAndSend("/queue/user/" + userId, payload);
        log.info("[WebSocket] 推送给用户: userId={}, title={}", userId, title);
    }
}