package com.example.smart_office;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync   // ★ 加这行，让 @Async 生效
@SpringBootApplication
public class SmartOfficeApplication {

	public static void main(String[] args) {
		SpringApplication.run(SmartOfficeApplication.class, args);
	}

	@Autowired
	private Environment env;

	@PostConstruct
	public void printConfig() {
		System.out.println("========== 配置检查 ==========");
		System.out.println("mqtt.broker = " + env.getProperty("mqtt.broker"));
		System.out.println("spring.datasource.url = " + env.getProperty("spring.datasource.url"));
		System.out.println("spring.data.redis.host = " + env.getProperty("spring.data.redis.host"));
		System.out.println("========== 配置检查结束 ==========");
	}

}
