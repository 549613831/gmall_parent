package com.atguigu.gmall.activity.controller;

import com.atguigu.gmall.activity.service.SeckillGoodsService;
import com.atguigu.gmall.common.constant.MqConst;
import com.atguigu.gmall.common.constant.RedisConst;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.common.result.ResultCodeEnum;
import com.atguigu.gmall.common.service.RabbitService;
import com.atguigu.gmall.common.util.AuthContextHolder;
import com.atguigu.gmall.common.util.CacheHelper;
import com.atguigu.gmall.common.util.DateUtil;
import com.atguigu.gmall.common.util.MD5;
import com.atguigu.gmall.model.activity.OrderRecode;
import com.atguigu.gmall.model.activity.SeckillGoods;
import com.atguigu.gmall.model.activity.UserRecode;
import com.atguigu.gmall.model.order.OrderDetail;
import com.atguigu.gmall.model.order.OrderInfo;
import com.atguigu.gmall.model.user.UserAddress;
import com.atguigu.gmall.order.client.OrderFeignClient;
import com.atguigu.gmall.product.client.ProductFeignClient;
import com.atguigu.gmall.user.client.UserFeignClient;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.commons.lang.StringUtils;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

@RestController
@RequestMapping("/api/activity/seckill")
public class SeckillGoodsController {
    @Autowired
    private SeckillGoodsService seckillGoodsService;
    @Autowired
    private UserFeignClient userFeignClient;
    @Autowired
    private ProductFeignClient productFeignClient;
    @Autowired
    private RabbitService rabbitService;
    @Autowired
    private RedisTemplate redisTemplate;
    @Autowired
    private OrderFeignClient orderFeignClient;

    @ApiOperation("返回全部列表")
    @GetMapping("/findAll")
    public Result<Object> findAll() {
        return Result.ok(seckillGoodsService.findAll());
    }

    @ApiOperation("获取实体")
    @GetMapping("/getSeckillGoods/{skuId}")
    public Result<Object> getSeckillGoods(@PathVariable("skuId") Long skuId) {
        return Result.ok(seckillGoodsService.getSeckillGoods(skuId));
    }

    @ApiOperation("生成下单码")
    @GetMapping("/auth/getSeckillSkuIdStr/{skuId}")
    public Result getSeckillSkuIdStr(@PathVariable Long skuId, HttpServletRequest request) {
        String userId = AuthContextHolder.getUserId(request);
        SeckillGoods seckillGoods = seckillGoodsService.getSeckillGoods(skuId);
        if (seckillGoods != null) {
            Date date = new Date();
            if (DateUtil.dateCompare(seckillGoods.getStartTime(), date) && DateUtil.dateCompare(date, seckillGoods.getEndTime())) {
                // 只有在秒杀时间范围内才能生成下单码
                String encrypt = MD5.encrypt(userId);
                return Result.ok(encrypt);
            }
        }

        return Result.fail().message("获取下单码失败");
    }

    @ApiOperation("根据用户和商品ID实现秒杀下单")
    @PostMapping("auth/seckillOrder/{skuId}")
    public Result seckillOrder(@PathVariable("skuId") Long skuId, HttpServletRequest request) {
        // 校验下单码
        String skuIdStr = request.getParameter("skuIdStr");
        String userId = AuthContextHolder.getUserId(request);
        String encrypt = MD5.encrypt(userId);
        if (!skuIdStr.equals(encrypt)) {
            // 下单码不同，秒杀失败
            return Result.build(null, ResultCodeEnum.SECKILL_ILLEGAL);
        }

        // 1:可以秒杀 0:不可以秒杀
        String status = (String) CacheHelper.get(skuId.toString());
        if (StringUtils.isEmpty(status)) {
            return Result.build(null, ResultCodeEnum.SECKILL_ILLEGAL);
        }

        if ("1".equals(status)) {
            // 用户记录
            UserRecode userRecode = new UserRecode();
            userRecode.setSkuId(skuId);
            userRecode.setUserId(userId);

            // 将用户当如消息队列中进行排队
            rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_SECKILL_USER, MqConst.ROUTING_SECKILL_USER, userRecode);
        } else {
            return Result.build(null, ResultCodeEnum.SECKILL_FINISH);
        }

        return Result.ok();
    }

    @ApiOperation("秒杀确认订单")
    @GetMapping("auth/trade")
    public Result trade(HttpServletRequest request) {
        // 获取用户id
        String userId = AuthContextHolder.getUserId(request);

        // 获取收货地址
        List<UserAddress> addressListByUserId = userFeignClient.findUserAddressListByUserId(userId);

        // 获取秒杀商品
        OrderRecode orderRecode = (OrderRecode) redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS).get(userId);
        if (orderRecode == null) {
            return Result.fail().message("非法操作");
        }
        SeckillGoods seckillGoods = orderRecode.getSeckillGoods();

        // 声明一个集合存储订单明细
        ArrayList<OrderDetail> detailArrayList = new ArrayList<>();
        OrderDetail orderDetail = new OrderDetail();
        orderDetail.setSkuId(seckillGoods.getSkuId());
        orderDetail.setSkuName(seckillGoods.getSkuName());
        orderDetail.setImgUrl(seckillGoods.getSkuDefaultImg());
        orderDetail.setSkuNum(orderRecode.getNum());
        orderDetail.setOrderPrice(seckillGoods.getCostPrice());
        detailArrayList.add(orderDetail);

        // 计算总金额
        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setOrderDetailList(detailArrayList);
        orderInfo.sumTotalAmount();

        HashMap<String, Object> map = new HashMap<>();
        map.put("userAddressList", addressListByUserId);
        map.put("detailArrayList", detailArrayList);
        map.put("totalAmount", orderInfo.getTotalAmount());

        return Result.ok(map);
    }

    @ApiOperation("轮询页面的状态")
    @GetMapping(value = "auth/checkOrder/{skuId}")
    public Result checkOrder(@PathVariable Long skuId,HttpServletRequest request){
        // 获取用户Id
        String userId = AuthContextHolder.getUserId(request);
        // 调用服务层的方法
        return seckillGoodsService.checkOrder(skuId,userId);
    }

    @ApiOperation("秒杀提交订单")
    @PostMapping("auth/submitOrder")
    public Result submitOrder(@RequestBody OrderInfo orderInfo, HttpServletRequest request) {
        String userId = AuthContextHolder.getUserId(request);

        OrderRecode orderRecode = (OrderRecode) redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS).get(userId);
        if (orderRecode == null) {
            return Result.fail().message("非法操作");
        }

        orderInfo.setUserId(Long.parseLong(userId));

        Long orderId = orderFeignClient.submitOrder(orderInfo);
        if (orderId == null) {
            return Result.fail().message("下单失败，请重新操作");
        }

        // 删除缓存中下单信息
        redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS).delete(userId);
        // 记录下单信息
        redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS_USERS).put(userId, orderId.toString());

        return Result.ok();
    }


}
