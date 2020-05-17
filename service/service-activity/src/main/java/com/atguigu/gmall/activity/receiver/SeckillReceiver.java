package com.atguigu.gmall.activity.receiver;

import com.atguigu.gmall.activity.mapper.SeckillGoodsMapper;
import com.atguigu.gmall.activity.service.SeckillGoodsService;
import com.atguigu.gmall.common.constant.MqConst;
import com.atguigu.gmall.common.constant.RedisConst;
import com.atguigu.gmall.common.util.DateUtil;
import com.atguigu.gmall.model.activity.SeckillGoods;
import com.atguigu.gmall.model.activity.UserRecode;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.util.Date;
import java.util.List;

@Component
public class SeckillReceiver {
    @Autowired
    private RedisTemplate redisTemplate;
    @Autowired
    private SeckillGoodsMapper seckillGoodsMapper;
    @Autowired
    private SeckillGoodsService seckillGoodsService;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = MqConst.QUEUE_TASK_1),
            exchange = @Exchange(value = MqConst.EXCHANGE_DIRECT_TASK),
            key = {MqConst.ROUTING_TASK_1}
    ))
    public void importItemToRedis(Message message, Channel channel) throws IOException {
        QueryWrapper<SeckillGoods> wrapper = new QueryWrapper<>();
        wrapper.eq("status", 1);
        // 秒杀开始时间为今天才能被扫描到
        wrapper.eq("DATE_FORMAT(start_time,'%Y-%m-%d')", DateUtil.formatDate(new Date()));
        List<SeckillGoods> goodsList = seckillGoodsMapper.selectList(wrapper);

        if (!CollectionUtils.isEmpty(goodsList)) {
            for (SeckillGoods seckillGood : goodsList) {
                // 判断缓存中是否有商品
                Boolean aBoolean = redisTemplate.boundHashOps(RedisConst.SECKILL_GOODS).hasKey(seckillGood.getSkuId().toString());
                if (aBoolean) {
                    // 如果商品已经存在缓存中，则不需要添加
                    continue;
                }
                redisTemplate.boundHashOps(RedisConst.SECKILL_GOODS).put(seckillGood.getSkuId().toString(), seckillGood);

                // 根据每一个商品的数量，将商品按队列的形式放进redis中，没一个商品数量存储一次
                for (int i = 0; i < seckillGood.getStockCount(); i++) {
                    redisTemplate.boundListOps(RedisConst.SECKILL_STOCK_PREFIX + seckillGood.getSkuId().toString())
                            .leftPush(seckillGood.getSkuId().toString());
                }

                // 通知添加与更新状态位，更新为开启
                redisTemplate.convertAndSend("seckillpush", seckillGood.getSkuId()+":1");
            }
        }

        channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
    }


    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = MqConst.QUEUE_SECKILL_USER, durable = "true"),
            exchange = @Exchange(value = MqConst.EXCHANGE_DIRECT_SECKILL_USER, type = ExchangeTypes.DIRECT, durable = "true"),
            key = {MqConst.ROUTING_SECKILL_USER}
    ))
    public void seckill(UserRecode userRecode, Message message, Channel channel) throws IOException {
        if (userRecode != null) {
            seckillGoodsService.seckillOrder(userRecode.getSkuId(), userRecode.getUserId());
        }

        channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
    }

    /**
     * 秒杀结束清空缓存
     * @param message
     * @param channel
     * @throws IOException
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = MqConst.QUEUE_TASK_18, durable = "true"),
            exchange = @Exchange(value = MqConst.EXCHANGE_DIRECT_TASK, type = ExchangeTypes.DIRECT, durable = "true"),
            key = {MqConst.ROUTING_TASK_18}
    ))
    public void clearRedis(Message message, Channel channel) throws IOException {
        // 活动结束，清除缓存
        QueryWrapper<SeckillGoods> wrapper = new QueryWrapper<>();
        wrapper.eq("status", 1);
        wrapper.le("end_time", new Date());
        List<SeckillGoods> goodsList = seckillGoodsMapper.selectList(wrapper);
        for (SeckillGoods seckillGoods : goodsList) {
            redisTemplate.delete(RedisConst.SECKILL_STOCK_PREFIX + seckillGoods.getSkuId());
        }

        redisTemplate.delete(RedisConst.SECKILL_GOODS);
        redisTemplate.delete(RedisConst.SECKILL_ORDERS);
        redisTemplate.delete(RedisConst.SECKILL_ORDERS_USERS);

        SeckillGoods seckillGoods = new SeckillGoods();
        seckillGoods.setStatus("2");
        seckillGoodsMapper.update(seckillGoods, wrapper);

        channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
    }

}
