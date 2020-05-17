package com.atguigu.gmall.payment.service;

import com.alipay.api.AlipayApiException;

public interface AlipayService {
    /**
     * 生成支付宝二维码
     * @param orderId 订单id
     * @return 二维码
     * @throws AlipayApiException 二维码生成异常
     */
    String createaliPay(Long orderId) throws AlipayApiException;

    /**
     * 支付宝退款
     * @param orderId 订单id
     * @return 退款结果
     */
    boolean refund(Long orderId);

    /***
     * 关闭交易
     * @param orderId 订单id
     * @return 关闭结果
     */
    Boolean closePay(Long orderId);

    /**
     * 查看支付宝是否有交易记录，不管是否支付成功
     * @param orderId 订单id
     * @return 支付结果
     */
    Boolean checkPayment(Long orderId);


}
