package com.atguigu.gmall.order.receiver;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.common.constant.MqConst;
import com.atguigu.gmall.common.service.RabbitService;
import com.atguigu.gmall.model.enums.OrderStatus;
import com.atguigu.gmall.model.enums.PaymentStatus;
import com.atguigu.gmall.model.enums.ProcessStatus;
import com.atguigu.gmall.model.order.OrderInfo;
import com.atguigu.gmall.model.payment.PaymentInfo;
import com.atguigu.gmall.order.service.OrderService;
import com.atguigu.gmall.payment.client.PaymentFeignClient;
import com.rabbitmq.client.Channel;
import lombok.SneakyThrows;
import org.apache.commons.lang.StringUtils;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

@Component
public class OrderReceiver {
    @Autowired
    private OrderService orderService;
    @Autowired
    private PaymentFeignClient paymentFeignClient;
    @Autowired
    private RabbitService rabbitService;

    @RabbitListener(queues = MqConst.QUEUE_ORDER_CANCEL)
    public void orderCancel(Long orderId, Message message, Channel channel) throws IOException {
        if (orderId != null) {
            OrderInfo orderInfo = orderService.getOrderInfo(orderId);
            if (orderInfo != null) {
                // 远程调用接口查询支付信息
                PaymentInfo paymentInfo = paymentFeignClient.getPaymentInfo(orderInfo.getOutTradeNo());
                // 如果支付信息不为空，并且有交易记录，交易记录在生成二维码时保存
                if (paymentInfo != null && paymentInfo.getPaymentStatus().equals(PaymentStatus.UNPAID.name())) {
                    // 远程调用查看用户是否进行扫码支付
                    Boolean aBoolean = paymentFeignClient.checkPayment(orderId);
                    if (aBoolean) {
                        // 支付宝已经生成订单，关闭
                        Boolean closePay = paymentFeignClient.closePay(orderId);
                        if (closePay) {
                            // 如果返回true，证明用户没有付款
                            orderService.execExpiredOrder(orderId, "2");
                        } else {
                            // 用户已经付款成功，则不能关闭订单
                            rabbitService.sendMessage(
                                    MqConst.EXCHANGE_DIRECT_PAYMENT_PAY,
                                    MqConst.ROUTING_PAYMENT_PAY,
                                    paymentInfo.getOrderId());
                        }
                    } else {
                        // 用户没有扫描二维码
                        orderService.execExpiredOrder(orderId, "2");
                    }
                } else {
                    // 说明paymentInfo没有数据，没有生成二维码，支付宝也没有交易信息，所以只关闭订单
                    orderService.execExpiredOrder(orderId, "1");
                }
            }
        }

        // 手动确认消息
        channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = MqConst.QUEUE_PAYMENT_PAY,durable = "true"),
            exchange = @Exchange(value = MqConst.EXCHANGE_DIRECT_PAYMENT_PAY),
            key = {MqConst.ROUTING_PAYMENT_PAY}
    ))
    public void paySuccess(Long orderId, Message message, Channel channel) throws IOException {
        if (orderId != null) {
            OrderInfo orderInfo = orderService.getById(orderId);
            if (orderInfo != null && orderInfo.getOrderStatus().equals(OrderStatus.UNPAID.name())) {
                // 支付成功，修改订单状态为已支付
                orderService.updateOrderStatus(orderId, ProcessStatus.PAID);
                // 发送消息，通知仓库
                orderService.sendOrderStatus(orderId);
            }
        }
        channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
    }

    @SneakyThrows
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = MqConst.QUEUE_WARE_ORDER),
            exchange = @Exchange(value = MqConst.EXCHANGE_DIRECT_WARE_ORDER),
            key = {MqConst.ROUTING_WARE_ORDER}
    ))
    public void updateOrderStatus(String msgJson, Message message, Channel channel) {
        if (!StringUtils.isEmpty(msgJson)) {
            Map<String,Object> map = JSON.parseObject(msgJson, Map.class);
            String orderId = (String) map.get("orderId");
            String status = (String) map.get("status");
            if ("DEDUCTED".equals(status)) {
                // 减库存成功，修改订单状态为代发货
                orderService.updateOrderStatus(Long.parseLong(orderId), ProcessStatus.WAITING_DELEVER);
            } else {
                // 减库存失败
                orderService.updateOrderStatus(Long.parseLong(orderId),ProcessStatus.STOCK_EXCEPTION);
            }
        }

        channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
    }
}
