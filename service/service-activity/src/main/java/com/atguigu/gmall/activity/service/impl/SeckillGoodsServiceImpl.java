package com.atguigu.gmall.activity.service.impl;

import com.atguigu.gmall.activity.mapper.SeckillGoodsMapper;
import com.atguigu.gmall.activity.service.SeckillGoodsService;
import com.atguigu.gmall.common.constant.RedisConst;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.common.result.ResultCodeEnum;
import com.atguigu.gmall.common.util.CacheHelper;
import com.atguigu.gmall.common.util.MD5;
import com.atguigu.gmall.model.activity.OrderRecode;
import com.atguigu.gmall.model.activity.SeckillGoods;
import jodd.util.StringUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class SeckillGoodsServiceImpl implements SeckillGoodsService {
    @Autowired
    private SeckillGoodsMapper seckillGoodsMapper;
    @Autowired
    private RedisTemplate redisTemplate;

    @Override
    public List<SeckillGoods> findAll() {
        return redisTemplate.boundHashOps(RedisConst.SECKILL_GOODS).values();
    }

    @Override
    public SeckillGoods getSeckillGoods(Long id) {
        return (SeckillGoods) redisTemplate.boundHashOps(RedisConst.SECKILL_GOODS).get(id.toString());
    }

    @Override
    public void seckillOrder(Long skuId, String userId) {
        String state = (String) CacheHelper.get(skuId.toString());
        if ("0".equals(state)) {
            // 已售罄
            return;
        }

        // 判断用户是否下过单
        Boolean aBoolean = redisTemplate.opsForValue().setIfAbsent(RedisConst.SECKILL_USER + userId, skuId, RedisConst.SECKILL__TIMEOUT, TimeUnit.SECONDS);
        if (!aBoolean) {
            // 如果用户已经秒杀过，就不能再继续购买
            return;
        }

        // 获取队列中的商品
        String goodsId = (String) redisTemplate.boundListOps(RedisConst.SECKILL_STOCK_PREFIX + skuId).rightPop();
        if (StringUtil.isEmpty(goodsId)) {
            // 如果取不到商品，证明商品库存已空，更新状态位
            redisTemplate.convertAndSend("seckillpush", skuId + ":0");
            return;
        }

        // 生成订单记录
        OrderRecode orderRecode = new OrderRecode();
        orderRecode.setNum(1);
        orderRecode.setUserId(userId);
        orderRecode.setSeckillGoods(getSeckillGoods(skuId));
        // 设置下单码
        orderRecode.setOrderStr(MD5.encrypt(userId));

        // 订单数据存入redis
        redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS).put(orderRecode.getUserId(), orderRecode);
        // 更新库存
        updateStockCount(orderRecode.getSeckillGoods().getSkuId());
    }

    @Override
    public Result checkOrder(Long skuId, String userId) {
        // 判断用户是否在缓存中，如果存在则有机会下单
        Boolean aBoolean = redisTemplate.hasKey(RedisConst.SECKILL_USER + userId);
        if (aBoolean) {
            // 判断是否抢单成功
            Boolean hasKey = redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS).hasKey(userId);
            if (hasKey) {
                OrderRecode orderRecode = (OrderRecode) redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS).get(userId);
                return Result.build(orderRecode, ResultCodeEnum.SECKILL_SUCCESS);
            }
        }

        // 判断是否下过订单
        Boolean isExistOrder = redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS_USERS).hasKey(userId);
        if (isExistOrder) {
            // 下单成功
            String orderId = (String) redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS_USERS).get(userId);
            return Result.build(orderId, ResultCodeEnum.SECKILL_ORDER_SUCCESS);
        }

        String status = (String) CacheHelper.get(skuId.toString());
        if ("0".equals(status)) {
            // 已售罄，秒杀失败
            return Result.build(null, ResultCodeEnum.SECKILL_FINISH);
        }

        // 排队中
        return Result.build(null, ResultCodeEnum.SECKILL_RUN);
    }

    /**
     * 更新商品库存
     * @param skuId 商品id
     */
    private void updateStockCount(Long skuId) {
        Long count = redisTemplate.boundListOps(RedisConst.SECKILL_STOCK_PREFIX + skuId).size();
        if (count % 2 == 0) {
            // 更新数据库
            SeckillGoods seckillGoods = getSeckillGoods(skuId);
            seckillGoods.setStockCount(count.intValue());
            seckillGoodsMapper.updateById(seckillGoods);

            // 更新缓存
            redisTemplate.boundHashOps(RedisConst.SECKILL_GOODS).put(seckillGoods.getSkuId().toString(), seckillGoods);
        }
    }
}
