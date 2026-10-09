//消费注册队列，异步通知
package com.example.smart_office.service;

import com.example.smart_office.config.RabbitMQConfig;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component // 这个注解至关重要，告诉 SpringBoot 要管理这个类
public class RegisterMessageConsumer {

    // 监听 register.queue 队列，只要队列里有消息，这个方法就会自动触发
    @RabbitListener(queues = RabbitMQConfig.REGISTER_QUEUE)
    public void handleRegisterMessage(String message) {
        System.out.println("=== 消费者收到消息: " + message + "，开始异步发邮件 ===");

        // 这里模拟发邮件的过程（故意睡 2 秒，表示发邮件很慢）
        try {
            Thread.sleep(2);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        System.out.println("=== 邮件发送完成 ===");
    }
}