package com.atguigu.gmall.mq.config;

import org.springframework.amqp.core.Binding;

import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;

/**
 * @Author 伊塔
 * @Description 死信交换机设置
 * @Date 15:09 2020/5/11
 */
@Configuration
public class DeadLetterMqConfig {
    public static final String exchange_dead = "exchange.dead";
    public static final String routing_dead_1 = "routing.dead.1";
    public static final String routing_dead_2 = "routing.dead.2";
    public static final String queue_dead_1 = "queue.dead.1";
    public static final String queue_dead_2 = "queue.dead.2";

    @Bean
    public DirectExchange exchange() {
        return new DirectExchange(exchange_dead, true, false, null);
    }

    @Bean
    public Queue normalQueue() {
        HashMap<String, Object> map = new HashMap<>();
        map.put("x-dead-letter-exchange", exchange_dead);
        map.put("x-dead-letter-routing-key", routing_dead_2);
        map.put("x-message-ttl", 10 * 1000);

        return new Queue(queue_dead_1, true, false, false, map);
    }

    @Bean
    public Binding binding() {
        // 设置队列与交换机的绑定
        return BindingBuilder.bind(normalQueue()).to(exchange()).with(routing_dead_1);
    }

    @Bean
    public Queue deadQueue() {
        return new Queue(queue_dead_2, true, false, false, null);
    }

    @Bean
    public Binding deadBinding() {
        return BindingBuilder.bind(deadQueue()).to(exchange()).with(routing_dead_2);
    }
}
