package com.example.smart_office.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket 配置
 *
 * <p>使用 STOMP 协议（基于 WebSocket 的简单文本消息协议）。
 * <p>前端连接：ws://localhost:8080/ws
 * <p>订阅地址：/topic/alerts
 * <p>发送地址：/app/xxx
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // 启用简单的内存消息代理
        // /topic 前缀：用于广播（一对多）
        // /queue 前缀：用于点对点（一对一，后续可扩展）
        config.enableSimpleBroker("/topic", "/queue");

        // 客户端发送消息的前缀（前端用 @MessageMapping 接收）
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 1. 给原生 WebSocket 用的端点
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*");

        // 注册 STOMP 端点，前端连接 ws://localhost:8080/ws
        // setAllowedOriginPatterns("*") 允许跨域（生产环境应限制）
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();   // 支持 SockJS 降级（兼容老浏览器）
    }
}