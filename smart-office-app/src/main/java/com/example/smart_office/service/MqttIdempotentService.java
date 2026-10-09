//Redis 幂等校验
package com.example.smart_office.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class MqttIdempotentService {

    private static final String KEY_PREFIX = "mqtt:idempotent:";
    private static final Duration TTL = Duration.ofHours(24);

    @Autowired
    private StringRedisTemplate redisTemplate;

    /**
     * 尝试获取幂等锁。
     * @return true = 首次处理；false = 重复消息
     */
    public boolean tryAcquire(String messageId) {
        Boolean success = redisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + messageId, "1", TTL);
        return Boolean.TRUE.equals(success);
    }
}