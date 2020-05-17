package com.atguigu.gmall.order.service.impl;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.common.constant.MqConst;
import com.atguigu.gmall.common.service.RabbitService;
import com.atguigu.gmall.common.util.HttpClientUtil;
import com.atguigu.gmall.model.enums.OrderStatus;
import com.atguigu.gmall.model.enums.ProcessStatus;
import com.atguigu.gmall.model.order.OrderDetail;
import com.atguigu.gmall.model.order.OrderInfo;
import com.atguigu.gmall.order.mapper.OrderDetailMapper;
import com.atguigu.gmall.order.mapper.OrderInfoMapper;
import com.atguigu.gmall.order.service.OrderService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.*;

@Service
public class OrderServiceImpl extends ServiceImpl<OrderInfoMapper, OrderInfo> implements OrderService {
    @Value("${ware.url}")
    private String WARE_URL;

    @Autowired
    private OrderInfoMapper orderInfoMapper;
    @Autowired
    private OrderDetailMapper orderDetailMapper;
    @Autowired
    private RedisTemplate redisTemplate;
    @Autowired
    private RabbitService rabbitService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveOrderInfo(OrderInfo orderInfo) {
        // 总金额
        orderInfo.sumTotalAmount();

        orderInfo.setOrderStatus(OrderStatus.UNPAID.name());

        // 第三方交易编号
        String outTradeNo = "ATGUIGU" + System.currentTimeMillis() + new Random().nextInt(1000);
        orderInfo.setOutTradeNo(outTradeNo);
        System.out.println(outTradeNo);

        // 创建订单时间
        orderInfo.setCreateTime(new Date());

        // 订单过期时间
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DATE, 1);
        orderInfo.setExpireTime(calendar.getTime());
        System.out.println(calendar.getTime());

        // 获取订单明细，设置订单描述
        List<OrderDetail> orderDetailList = orderInfo.getOrderDetailList();
        StringBuilder stringBuilder = new StringBuilder();
        for (OrderDetail orderDetail : orderDetailList) {
            String skuName = orderDetail.getSkuName();
            stringBuilder.append(skuName);
        }

        if (stringBuilder.length() > 100) {
            // 订单描述有可能过长，截断
            orderInfo.setTradeBody(stringBuilder.toString().substring(0,100));
        } else {
            orderInfo.setTradeBody(stringBuilder.toString());
        }

        // 进程状态
        orderInfo.setProcessStatus(ProcessStatus.UNPAID.name());
        orderInfoMapper.insert(orderInfo);

        // 保存订单明细
        for (OrderDetail orderDetail : orderDetailList) {
            // orderInfo的id在存入数据库时自动添加
            orderDetail.setOrderId(orderInfo.getId());
            orderDetailMapper.insert(orderDetail);
        }

        // 保存订单后，发送延迟消息，取消订单
        rabbitService.sendDelayMessage(MqConst.EXCHANGE_DIRECT_ORDER_CANCEL,
                MqConst.ROUTING_ORDER_CANCEL,
                orderInfo.getId(),
                MqConst.DELAY_TIME);

        return orderInfo.getId();
    }

    @Override
    public String getTradeNo(String userId) {
        // 定义key
        String tradeNoKey = "user:" + userId + ":tradeCode";
        String tradeNo  = UUID.randomUUID().toString().replace("-", "");

        // 保存在redis中
        redisTemplate.opsForValue().set(tradeNoKey, tradeNo);

        return tradeNo;
    }

    @Override
    public boolean checkTradeCode(String userId, String tradeCodeNo) {
        // 定义key
        String tradeNoKey = "user:" + userId + ":tradeCode";

        // 从redis中取出流水号，并判断是否相同
        String tradeNo = (String) redisTemplate.opsForValue().get(tradeNoKey);

        assert tradeNo != null;
        return tradeNo.equals(tradeCodeNo);
    }

    @Override
    public void deleteTradeNo(String userId) {
        // 删除redis中的流水号
        String tradeNoKey = "user:" + userId + ":tradeCode";
        redisTemplate.delete(tradeNoKey);
    }

    @Override
    public boolean checkStock(Long skuId, Integer skuNum) {
        String result = HttpClientUtil.doGet(WARE_URL + "/hasStock?skuId=" + skuId + "&num=" + skuNum);
        return "1".equals(result);
    }

    @Override
    public void execExpiredOrder(Long orderId) {
        updateOrderStatus(orderId, ProcessStatus.CLOSED);

        // 发送消息，关闭支付宝订单
        rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_PAYMENT_CLOSE, MqConst.ROUTING_PAYMENT_CLOSE, orderId);
    }

    @Override
    public void updateOrderStatus(Long orderId, ProcessStatus processStatus) {
        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setId(orderId);
        orderInfo.setProcessStatus(processStatus.name());
        orderInfo.setOrderStatus(processStatus.getOrderStatus().name());
        orderInfoMapper.updateById(orderInfo);
    }

    @Override
    public OrderInfo getOrderInfo(Long orderId) {
        // 根据orderID查询订单
        OrderInfo orderInfo = orderInfoMapper.selectById(orderId);

        if (orderInfo == null) {
            return null;
        }
        // 查询订单明细
        QueryWrapper<OrderDetail> wrapper = new QueryWrapper<>();
        wrapper.eq("order_id", orderId);
        List<OrderDetail> orderDetails = orderDetailMapper.selectList(wrapper);
        orderInfo.setOrderDetailList(orderDetails);

        return orderInfo;
    }

    @Override
    public void sendOrderStatus(Long orderId) {
        // 根据订单id修改订单进程状态
        updateOrderStatus(orderId, ProcessStatus.NOTIFIED_WARE);

        String wareJson = initWareOrder(orderId);

        rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_WARE_STOCK, MqConst.ROUTING_WARE_STOCK, wareJson);
    }

    /**
     * 根据订单id获取包含部分orderInfo参数的json串
     * @param orderId 订单id
     * @return 包含部分orderInfo参数的json串
     */
    private String initWareOrder(Long orderId) {
        OrderInfo orderInfo = getOrderInfo(orderId);
        Map<String,Object> map = initWareOrder(orderInfo);
        return JSON.toJSONString(map);
    }

    /**
     * 根据订单获取包含部分orderInfo参数的map集合
     * @param orderInfo 订单id
     * @return 包含部分orderInfo参数的map集合
     */
    @Override
    public Map<String, Object> initWareOrder(OrderInfo orderInfo) {
        HashMap<String, Object> map = new HashMap<>();
        map.put("orderId", orderInfo.getId());
        map.put("consignee", orderInfo.getConsignee());
        map.put("consigneeTel", orderInfo.getConsigneeTel());
        map.put("orderComment", orderInfo.getOrderComment());
        map.put("orderBody", orderInfo.getTradeBody());
        map.put("deliveryAddress", orderInfo.getDeliveryAddress());
        map.put("paymentWay", "2");
        //map.put("wareId", orderInfo.getWareId());// 仓库Id ，减库存拆单时需要使用！
        ArrayList<Map<String, Object>> mapArrayList = new ArrayList<>();
        List<OrderDetail> orderDetailList = orderInfo.getOrderDetailList();
        for (OrderDetail orderDetail : orderDetailList) {
            HashMap<String, Object> orderDetailMap = new HashMap<>();
            orderDetailMap.put("skuId", orderDetail.getSkuId());
            orderDetailMap.put("skuNum", orderDetail.getSkuNum());
            orderDetailMap.put("skuName", orderDetail.getSkuName());
            mapArrayList.add(orderDetailMap);
        }
        map.put("details", mapArrayList);

        return map;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<OrderInfo> orderSplit(long orderId, String wareSkuMap) {
        ArrayList<OrderInfo> infoArrayList = new ArrayList<>();
        // 获取原始订单
        OrderInfo orderInfoOrigin = getOrderInfo(orderId);
        
        // 将字符串转换成可以操纵的对象
        List<Map> maps = JSON.parseArray(wareSkuMap, Map.class);
        if (maps != null) {
            for (Map map : maps) {
                String wareId = (String) map.get("wareId");
                List<String> skuIds = (List<String>) map.get("skuIds");

                OrderInfo subOrderInfo = new OrderInfo();
                // 将原始订单的数据赋给子订单
                BeanUtils.copyProperties(orderInfoOrigin, subOrderInfo);
                // 注意主键要自增
                subOrderInfo.setId(null);
                // 设置父订单
                subOrderInfo.setParentOrderId(orderId);
                // 赋值仓库id
                subOrderInfo.setWareId(wareId);

                // 获取原始订单的订单明细
                List<OrderDetail> orderDetailList = orderInfoOrigin.getOrderDetailList();
                // 创建集合保存子订单明细
                ArrayList<OrderDetail> subOrderDetails = new ArrayList<>();
                if (!CollectionUtils.isEmpty(orderDetailList)) {
                    // 如果原始订单的订单明细不为空
                    for (OrderDetail orderDetail : orderDetailList) {
                        for (String skuId : skuIds) {
                            if (Long.parseLong(skuId) == orderDetail.getSkuId()) {
                                // 判断是否有对应商品id
                                subOrderDetails.add(orderDetail);
                            }
                        }
                    }
                }
                // 设置子订单的订单明细
                subOrderInfo.setOrderDetailList(subOrderDetails);
                // 计算总金额
                subOrderInfo.sumTotalAmount();
                // 保存子订单
                saveOrderInfo(subOrderInfo);
                // 添加到集合中
                infoArrayList.add(subOrderInfo);
            }
        }
        // 修改订单状态
        updateOrderStatus(orderId, ProcessStatus.SPLIT);
        return infoArrayList;
    }

    @Override
    public void execExpiredOrder(Long orderId, String sign) {
        updateOrderStatus(orderId, ProcessStatus.CLOSED);

        if ("2".equals(sign)) {
            // 发送消息，关闭支付宝订单
            rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_PAYMENT_CLOSE, MqConst.ROUTING_PAYMENT_CLOSE, orderId);
        }
    }
}
