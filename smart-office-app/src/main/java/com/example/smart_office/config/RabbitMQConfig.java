//声明队列、交换机、TTL、死信
package com.example.smart_office.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {
    // 定义队列的名字，以后发消息和收消息都要用这个名字
    public static final String REGISTER_QUEUE = "register.queue";



    @Bean
    public Queue registerQueue() {
        // 创建一个名叫 register.queue 的队列
        // 第二个参数 true 代表“持久化”，意思是服务器重启后，队列里的消息不会丢
        return new Queue(REGISTER_QUEUE, true);
    }

    // 审批延迟队列
    public static final String APPROVAL_DELAY_QUEUE = "approval.delay.queue";
    public static final String APPROVAL_DLX_QUEUE = "approval.dlx.queue";
    public static final String APPROVAL_DELAY_EXCHANGE = "approval.delay.exchange";
    public static final String APPROVAL_DLX_EXCHANGE = "approval.dlx.exchange";
    public static final String APPROVAL_DELAY_ROUTING_KEY = "approval.delay";
    public static final String APPROVAL_DLX_ROUTING_KEY = "approval.dlx";

    /**
     * 延迟队列：无消费者，消息 TTL 过期后进死信队列
     */
    @Bean
    public Queue approvalDelayQueue() {
        return QueueBuilder.durable(APPROVAL_DELAY_QUEUE)
                .ttl(30 * 60 * 1000)   // TTL = 30 分钟
//                .ttl(10 * 1000)   // TTL = 10 秒（测试用）
                .deadLetterExchange(APPROVAL_DLX_EXCHANGE)
                .deadLetterRoutingKey(APPROVAL_DLX_ROUTING_KEY)
                .build();
    }

    /**
     * 死信队列：有消费者，处理过期消息
     */
    @Bean
    public Queue approvalDlxQueue() {
        return QueueBuilder.durable(APPROVAL_DLX_QUEUE).build();
    }

    @Bean
    public DirectExchange approvalDelayExchange() {
        return new DirectExchange(APPROVAL_DELAY_EXCHANGE);
    }

    @Bean
    public DirectExchange approvalDlxExchange() {
        return new DirectExchange(APPROVAL_DLX_EXCHANGE);
    }

    @Bean
    public Binding approvalDelayBinding() {
        return BindingBuilder.bind(approvalDelayQueue())
                .to(approvalDelayExchange())
                .with(APPROVAL_DELAY_ROUTING_KEY);
    }

    @Bean
    public Binding approvalDlxBinding() {
        return BindingBuilder.bind(approvalDlxQueue())
                .to(approvalDlxExchange())
                .with(APPROVAL_DLX_ROUTING_KEY);
    }







    // ============================================
// AI 告警队列（新增）
// ============================================
    public static final String AI_ALERT_QUEUE = "ai.alert.queue";
    public static final String AI_ALERT_EXCHANGE = "ai.alert.exchange";
    public static final String AI_ALERT_ROUTING_KEY = "ai.alert";
    public static final String AI_ALERT_DLX_QUEUE = "ai.alert.dlx.queue";
    public static final String AI_ALERT_DLX_EXCHANGE = "ai.alert.dlx.exchange";
    public static final String AI_ALERT_DLX_ROUTING_KEY = "ai.alert.dlx";

    @Bean
    public Queue aiAlertQueue() {
        return QueueBuilder.durable(AI_ALERT_QUEUE)
                .deadLetterExchange(AI_ALERT_DLX_EXCHANGE)
                .deadLetterRoutingKey(AI_ALERT_DLX_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue aiAlertDlxQueue() {
        return QueueBuilder.durable(AI_ALERT_DLX_QUEUE).build();
    }

    @Bean
    public DirectExchange aiAlertExchange() {
        return new DirectExchange(AI_ALERT_EXCHANGE);
    }

    @Bean
    public DirectExchange aiAlertDlxExchange() {
        return new DirectExchange(AI_ALERT_DLX_EXCHANGE);
    }

    @Bean
    public Binding aiAlertBinding() {
        return BindingBuilder.bind(aiAlertQueue())
                .to(aiAlertExchange())
                .with(AI_ALERT_ROUTING_KEY);
    }

    @Bean
    public Binding aiAlertDlxBinding() {
        return BindingBuilder.bind(aiAlertDlxQueue())
                .to(aiAlertDlxExchange())
                .with(AI_ALERT_DLX_ROUTING_KEY);
    }
}