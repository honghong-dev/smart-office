package com.example.smart_office.controller;

import com.example.smart_office.service.notification.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 测试通知接口（用于前端联调）
 */
@RestController
public class NotificationController {

    @Autowired
    private NotificationService notificationService;

    @GetMapping("/api/test/notify/broadcast")
    public String broadcast(@RequestParam String title) {
        notificationService.broadcastAlert(title, "这是一条测试告警", "WARNING");
        return "OK";
    }

    @GetMapping("/api/test/notify/user")
    public String sendToUser(@RequestParam String userId, @RequestParam String title) {
        notificationService.sendToUser(userId, title, "这是一条测试私信");
        return "OK";
    }
}