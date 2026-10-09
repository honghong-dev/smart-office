//监听 permission:refresh 频道
package com.example.smart_office.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
public class PermissionCacheRefreshListener {

    private static final Logger log = LoggerFactory.getLogger(PermissionCacheRefreshListener.class);

    public static final String CHANNEL = "permission:refresh";

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Bean
    public RedisMessageListenerContainer permissionRefreshContainer(RedisConnectionFactory factory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        container.addMessageListener((message, pattern) -> {
            String userId = new String(message.getBody());
            String key = "user:permissions:" + userId;
            redisTemplate.delete(key);
            log.info("权限缓存已刷新: userId={}", userId);
        }, new ChannelTopic(CHANNEL));
        return container;
    }
}