package com.atguigu.gmall.order.controller;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.cart.client.CartFeignClient;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.common.util.AuthContextHolder;
import com.atguigu.gmall.model.cart.CartInfo;
import com.atguigu.gmall.model.order.OrderDetail;
import com.atguigu.gmall.model.order.OrderInfo;
import com.atguigu.gmall.model.user.UserAddress;
import com.atguigu.gmall.order.service.OrderService;
import com.atguigu.gmall.product.client.ProductFeignClient;
import com.atguigu.gmall.user.client.UserFeignClient;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("api/order")
public class OrderApiController {
    @Autowired
    private UserFeignClient userFeignClient;
    @Autowired
    private CartFeignClient cartFeignClient;
    @Autowired
    private OrderService orderService;
    @Autowired
    private ProductFeignClient productFeignClient;

    @ApiOperation("确认订单")
    @GetMapping("auth/trade")
    public Result<Map<String, Object>> trade(HttpServletRequest request) {
        // 获取用户id
        String userId = AuthContextHolder.getUserId(request);
        // 获取收货地址
        List<UserAddress> userAddressList = userFeignClient.findUserAddressListByUserId(userId);

        // 获取被选中的商品
        List<CartInfo> cartCheckedList = cartFeignClient.getCartCheckedList(userId);

        int count = 0;
        // 存储订单明细集合
        ArrayList<OrderDetail> orderDetailList = new ArrayList<>();
        if (!CollectionUtils.isEmpty(cartCheckedList)) {
            for (CartInfo cartInfo : cartCheckedList) {
                OrderDetail orderDetail = new OrderDetail();
                orderDetail.setImgUrl(cartInfo.getImgUrl());
                orderDetail.setSkuId(cartInfo.getSkuId());
                orderDetail.setSkuName(cartInfo.getSkuName());
                orderDetail.setSkuNum(cartInfo.getSkuNum());
                orderDetail.setOrderPrice(cartInfo.getSkuPrice());
                count += cartInfo.getSkuNum();

                orderDetailList.add(orderDetail);
            }
        }

        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setOrderDetailList(orderDetailList);
        // 计算总金额
        orderInfo.sumTotalAmount();

        // 获取流水号并返回
        String tradeNo = orderService.getTradeNo(userId);

        HashMap<String, Object> map = new HashMap<>();
        map.put("totalNum", count);
        map.put("totalAmount", orderInfo.getTotalAmount());
        map.put("userAddressList", userAddressList);
        map.put("detailArrayList", orderDetailList);
        map.put("tradeNo", tradeNo);

        return Result.ok(map);
    }

    @ApiOperation("提交订单")
    @PostMapping("auth/submitOrder")
    public Result<Object> submitOrder(
            @RequestBody OrderInfo orderInfo,
            HttpServletRequest request){
        // 获取用户ID
        String userId = AuthContextHolder.getUserId(request);
        orderInfo.setUserId(Long.valueOf(userId));

        // 保存订单前先判断流水号是否相同
        String tradeNo = request.getParameter("tradeNo");
        boolean flag = orderService.checkTradeCode(userId, tradeNo);

        if (!flag) {
            // 如果流水号不相同，则不能创建订单
            return Result.fail().message("不能重复提交订单！");
        }

        // 删除流水号
        orderService.deleteTradeNo(userId);

        // 判断库存是否足够
        List<OrderDetail> orderDetailList = orderInfo.getOrderDetailList();
        if (!CollectionUtils.isEmpty(orderDetailList)) {
            for (OrderDetail orderDetail : orderDetailList) {
                boolean enough = orderService.checkStock(orderDetail.getSkuId(), orderDetail.getSkuNum());
                if (!enough) {
                    return Result.fail().message(orderDetail.getSkuName() + "库存不足！！");
                }

                // 验证价格是否发生变化
                BigDecimal skuPrice = productFeignClient.getSkuPrice(orderDetail.getSkuId());
                if (skuPrice.compareTo(orderDetail.getOrderPrice()) != 0) {
                    // 价格发生变化，需要重新加载订单数据


                    return Result.fail().message(orderDetail.getSkuName() + "金额变化，请重新下单！！");
                }
            }
        }

        // 保存订单
        Long orderId = orderService.saveOrderInfo(orderInfo);

        return Result.ok(orderId);
    }

    @ApiOperation("内部调用获取订单")
    @GetMapping("inner/getOrderInfo/{orderId}")
    public OrderInfo getOrderInfo(@PathVariable(value = "orderId") Long orderId){
        return orderService.getOrderInfo(orderId);
    }

    @ApiOperation("拆单业务")
    @RequestMapping("orderSplit")
    public String orderSplit(HttpServletRequest request){
        String orderId = request.getParameter("orderId");
        String wareSkuMap = request.getParameter("wareSkuMap");

        List<OrderInfo> subOrderInfoList = orderService.orderSplit(Long.parseLong(orderId), wareSkuMap);

        ArrayList<Map<String, Object>> mapArrayList = new ArrayList<>();
        for (OrderInfo orderInfo : subOrderInfoList) {
            Map<String, Object> map = orderService.initWareOrder(orderInfo);
            mapArrayList.add(map);
        }

        return JSON.toJSONString(mapArrayList);
    }

    @ApiOperation("秒杀提交订单，秒杀订单不需要做前置判断，直接下单")
    @PostMapping("inner/seckill/submitOrder")
    public Long submitOrder(@RequestBody OrderInfo orderInfo) {
        Long orderId = orderService.saveOrderInfo(orderInfo);
        return orderId;
    }

}
