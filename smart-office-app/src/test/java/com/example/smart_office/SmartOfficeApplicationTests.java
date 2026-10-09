package com.example.smart_office;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

@SpringBootTest
class SmartOfficeApplicationTests {

	@Test
	void contextLoads() {
	}
	@Autowired
	private StringRedisTemplate redisTemplate;

	@Test
	public void testRedis() {
		redisTemplate.opsForValue().set("name", "xiaohong");
		String value = redisTemplate.opsForValue().get("name");
		System.out.println("从Redis获取的值: " + value);
	}
}
