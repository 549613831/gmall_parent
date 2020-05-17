package com.atguigu.gmall.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
@Slf4j
public class MQProducerAckConfig implements RabbitTemplate.ConfirmCallback,RabbitTemplate.ReturnCallback {
    @Autowired
    private RabbitTemplate rabbitTemplate;

    @PostConstruct
    private void init() {
        // @PostConstruct表示对象创建时，即调用构造器时执行，指定确认方法，否则不生效
        rabbitTemplate.setConfirmCallback(this);
        rabbitTemplate.setReturnCallback(this);
    }

    /**
     * 如果消息没有到交换机，则confirm方法执行，返回ack = false，如果消息到了交换机，ack = true，cause是原因
     * 如若正确到达，correlationData和cause都是null
     * @param correlationData 唯一编号
     * @param ack 消息是否到达交换机
     * @param cause 原因
     */
    @Override
    public void confirm(CorrelationData correlationData, boolean ack, String cause) {
        if (ack) {
            log.info("消息成功到达交换机");
            log.info("cause=" + cause);
            log.info("correlationData=" + correlationData);
        } else {
            log.info("消息没有成功到达交换机");
            log.info("cause=" + cause);
            log.info("correlationData=" + correlationData);
        }
    }

    /**
     * 如果消息从交换机正确绑定到队列，这个方法就不会执行，如果没有，则执行该方法
     * @param message 消息的内容
     * @param replyCode 消息码
     * @param replyText 消息码对应的内容
     * @param exchange 绑定的交换机
     * @param routingKey 绑定的routingKey
     */
    @Override
    public void returnedMessage(Message message, int replyCode, String replyText, String exchange, String routingKey) {
        // 反序列化对象输出
        System.out.println("消息主体: " + new String(message.getBody()));
        System.out.println("应答码: " + replyCode);
        System.out.println("描述：" + replyText);
        System.out.println("消息使用的交换器 exchange : " + exchange);
        System.out.println("消息使用的路由键 routing : " + routingKey);
    }
}
