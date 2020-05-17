package com.atguigu.gmall.mq.controller;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.common.service.RabbitService;
import com.atguigu.gmall.mq.config.DeadLetterMqConfig;
import com.atguigu.gmall.mq.config.DelayedMqConfig;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.text.SimpleDateFormat;
import java.util.Date;

@RestController
@RequestMapping("/mq")
@Slf4j
public class MqController {
    @Autowired
    private RabbitService rabbitService;
    @Autowired
    private RabbitTemplate rabbitTemplate;

    @ApiOperation("发送消息")
    @GetMapping("sendConfirm")
    public Result<Object> sendConfirm() {
        String message = "hello RabbitMQ!";
        rabbitService.sendMessage("exchange.confirm", "routing.confirm", message);
        return Result.ok();
    }

    @ApiOperation("发送死信消息")
    @GetMapping("sendDeadLetter")
    public Result<Object> sendDeadLetter() {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        // 方式一：指定每个消息的延时时间
        rabbitTemplate.convertAndSend(DeadLetterMqConfig.exchange_dead,
                DeadLetterMqConfig.routing_dead_1, "11");
        System.out.println(simpleDateFormat.format(new Date()) + "Delay sent.");
        return Result.ok();
    }

    @GetMapping("sendDelay")
    public Result<Object> sendDelay() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        rabbitTemplate.convertAndSend(
                DelayedMqConfig.exchange_delay,
                DelayedMqConfig.routing_delay,
                new Date(),
                message -> {
                    message.getMessageProperties().setDelay(1000 * 10);
                    System.out.println(format.format(new Date()) + " Delay sent.");
                    return message;
                });

        return Result.ok();
    }

}
