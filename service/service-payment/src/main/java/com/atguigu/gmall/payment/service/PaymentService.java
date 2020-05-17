package com.atguigu.gmall.payment.service;


import com.atguigu.gmall.model.order.OrderInfo;
import com.atguigu.gmall.model.payment.PaymentInfo;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.Map;

public interface PaymentService extends IService<PaymentInfo> {
    /**
     * 保存交易记录
     * @param orderInfo 订单信息
     * @param paymentType 支付类型（1：微信 2：支付宝）
     */
    void savePaymentInfo(OrderInfo orderInfo, String paymentType);

    /**
     * 通过对外业务编号查询交易记录
     * @param outTradeNo 支付宝业务编号
     * @param name 交易类型
     * @return 交易记录
     */
    PaymentInfo getPaymentInfo(String outTradeNo, String name);

    /**
     * 支付成功
     * @param outTradeNo 支付宝业务编号
     * @param name 支付类型
     * @param paramMap 支付宝异步回调参数
     */
    void paySuccess(String outTradeNo, String name, Map<String, String> paramMap);

    /**
     * 修改订单状态
     * @param outTradeNo 支付宝业务编号
     * @param name 支付类型
     * @param paymentInfo 修改参数
     */
    void updatePaymentInfo(String outTradeNo, String name, PaymentInfo paymentInfo);

    /**
     * 关闭过期订单的交易记录
     * @param orderId 订单id
     */
    void closePayment(Long orderId);
}
