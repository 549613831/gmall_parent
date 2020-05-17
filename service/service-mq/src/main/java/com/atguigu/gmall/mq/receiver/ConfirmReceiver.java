package com.atguigu.gmall.mq.receiver;

import com.atguigu.gmall.mq.config.DeadLetterMqConfig;
import com.atguigu.gmall.mq.config.DelayedMqConfig;
import com.rabbitmq.client.Channel;
import lombok.SneakyThrows;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Date;

@Component
@Configuration
public class ConfirmReceiver {
    @SneakyThrows
    @RabbitListener(
            bindings = @QueueBinding(
                    value = @Queue(value = "queue.confirm", autoDelete = "false"),
                    exchange = @Exchange(value = "exchange.confirm", autoDelete = "true"),
                    key = {"routing.confirm"}
            )
    )
    public void confirmMessage(Message message, Channel channel) {
        // 获取数据
        String str = new String(message.getBody());
        System.out.println("接收到的消息=" + str);


        try {
            int i = 1 / 0;

            // 第一个参数为long类型的id ，第二参数为false表示每次确认一个消息，true表示批量确认
            // 表示消息正确处理，手动签收
            channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
        } catch (Exception e) {
            System.out.println("出现异常");

            if (message.getMessageProperties().getRedelivered()) {
                System.out.println("消息已经处理过了");
                // 该消息被处理过 拒绝消息，requeue=false 表示不再重新入队，如果配置了死信队列则进入死信队列
                channel.basicReject(message.getMessageProperties().getDeliveryTag(), false);
            } else {
                System.out.println("消息即将返回队列");
                // 该消息没有被处理过，则重新进入消息队列 参数二：是否批量， 参数三：为是否重新回到队列，true重新入队
                channel.basicNack(message.getMessageProperties().getDeliveryTag(), false, true);
            }
        }
    }

    @RabbitListener(queues = DeadLetterMqConfig.queue_dead_2)
    public void get(String msg) {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        System.out.println("Receive:" + msg);
        System.out.println(simpleDateFormat.format(new Date()) + "Delay rece." + msg);
    }

    @RabbitListener(queues = DelayedMqConfig.queue_delay_1)
    public void getDelayMessage() {

    }
}
