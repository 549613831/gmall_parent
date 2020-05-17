package com.atguigu.gmall.order.service;

import com.atguigu.gmall.model.enums.ProcessStatus;
import com.atguigu.gmall.model.order.OrderInfo;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;
import java.util.Map;

public interface OrderService extends IService<OrderInfo> {
    /**
     * 保存订单
     * @param orderInfo 订单信息
     * @return 订单id
     */
    Long saveOrderInfo(OrderInfo orderInfo);

    /**
     * 生产流水号
     * @param userId 用户id
     * @return 流水号
     */
    String getTradeNo(String userId);

    /**
     * 比较流水号
     * @param userId 获取缓存中的流水号
     * @param tradeCodeNo   页面传递过来的流水号
     * @return 比较结果
     */
    boolean checkTradeCode(String userId, String tradeCodeNo);


    /**
     * 删除流水号
     * @param userId 用户id
     */
    void deleteTradeNo(String userId);

    /**
     * 验证库存
     * @param skuId 商品id
     * @param skuNum 商品名称
     * @return 判断库存是否足够
     */
    boolean checkStock(Long skuId, Integer skuNum);


    /**
     * 处理过期订单
     * @param orderId 订单ID
     */
    void execExpiredOrder(Long orderId);

    /**
     * 根据订单Id 修改订单的状态
     * @param orderId 订单id
     * @param processStatus 订单进程
     */
    void updateOrderStatus(Long orderId, ProcessStatus processStatus);

    /**
     * 根据订单Id 查询订单信息
     * @param orderId 订单Id
     * @return 订单信息
     */
    OrderInfo getOrderInfo(Long orderId);

    /**
     * 发送消息，通知仓库
     * @param orderId 订单id
     */
    void sendOrderStatus(Long orderId);

    /**
     * 将orderinfo部分参数封装成map
     * @param orderInfo 订单信息
     * @return map
     */
    Map<String, Object> initWareOrder(OrderInfo orderInfo);

    /**
     * 将原始订单拆分成子订单
     * @param orderId 订单id
     * @param wareSkuMap {订单仓库id:对应订单id} 有多个
     * @return list
     */
    List<OrderInfo> orderSplit(long orderId, String wareSkuMap);

    /**
     * 根据sign判断是否只关闭订单，或者同时关闭订单和支付宝交易记录
     * @param orderId 订单id
     * @param sign 标志位
     */
    void execExpiredOrder(Long orderId, String sign);
}
