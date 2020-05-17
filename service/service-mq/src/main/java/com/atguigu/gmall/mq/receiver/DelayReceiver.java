package com.atguigu.gmall.mq.receiver;

import com.atguigu.gmall.mq.config.DelayedMqConfig;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Date;

@Configuration
public class DelayReceiver {
    @RabbitListener(queues = DelayedMqConfig.queue_delay_1)
    public void delay(String msg) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        System.out.println("msg=" + msg);
        System.out.println("Receive queue_delay_1: " + format.format(new Date()) + " Delay rece." + msg);
    }
}
