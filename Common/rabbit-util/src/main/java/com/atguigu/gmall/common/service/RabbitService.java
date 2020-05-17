package com.atguigu.gmall.common.service;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RabbitService {
    @Autowired
    private RabbitTemplate rabbitTemplate;

    /**
     * 发送消息
     * @param exchange 交换机
     * @param routingKey routingKey
     * @param message 消息
     */
    public boolean sendMessage(String exchange, String routingKey, Object message) {
        rabbitTemplate.convertAndSend(exchange,routingKey,message);
        return true;
    }

    /**
     * 发送延迟消息
     * @param exchange   交换机
     * @param routingKey 路由键
     * @param message    消息
     * @param delayTime  单位：秒
     */
    public boolean sendDelayMessage(String exchange, String routingKey, Object message, int delayTime) {
        rabbitTemplate.convertAndSend(exchange,
                routingKey, message,
                message1 -> {
                    message1.getMessageProperties().setDelay(delayTime * 1000);
                    return message1;
                });

        return true;
    }
}
