package com.atguigu.gmall.payment.controller;

import com.alipay.api.AlipayApiException;
import com.alipay.api.internal.util.AlipaySignature;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.model.enums.PaymentStatus;
import com.atguigu.gmall.model.enums.PaymentType;
import com.atguigu.gmall.model.payment.PaymentInfo;
import com.atguigu.gmall.payment.config.AlipayConfig;
import com.atguigu.gmall.payment.service.AlipayService;
import com.atguigu.gmall.payment.service.PaymentService;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@Controller
@RequestMapping("/api/payment/alipay")
public class AlipayController {
    @Autowired
    private AlipayService alipayService;
    @Autowired
    private PaymentService paymentService;

    @RequestMapping("submit/{orderId}")
    @ResponseBody
    public String submitOrder(@PathVariable(value = "orderId") Long orderId) {
        String alipay = "";
        try {
            alipay = alipayService.createaliPay(orderId);
        } catch (AlipayApiException e) {
            e.printStackTrace();
        }
        return alipay;
    }

    @RequestMapping("callback/return")
    public String callBack() {
        // 同步回调给用户查看结果
        return "redirect:" + AlipayConfig.return_order_url;
    }

    /**
     * 支付宝异步回调  必须使用内网穿透
     * @param paramMap 异步回调参数
     * @return 支付结果
     */
    @RequestMapping("callback/notify")
    @ResponseBody
    public String alipayNotify(@RequestParam Map<String, String> paramMap) {
        System.out.println("异步回调");
        // 调用SDK验证签名
        boolean signVerified = false;
        try {
            signVerified = AlipaySignature.rsaCheckV1(
                    paramMap,
                    AlipayConfig.alipay_public_key,
                    AlipayConfig.charset,
                    AlipayConfig.sign_type);
        } catch (AlipayApiException e) {
            e.printStackTrace();
        }

        // 获得交易状态和支付宝交易号
        String tradeStatus = paramMap.get("trade_status");
        String outTradeNo = paramMap.get("out_trade_no");

        if (signVerified) {
            // 验签通过，根据支付宝提供的信息进行二次校验
            if ("TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus)) {
                PaymentInfo paymentInfo = paymentService.getPaymentInfo(outTradeNo, PaymentType.ALIPAY.name());
                // 只有交易通知状态为 TRADE_SUCCESS 或 TRADE_FINISHED 时，支付宝才会认定为买家付款成功。

                if (paymentInfo.getPaymentStatus().equals(PaymentStatus.ClOSED.name())
                        || paymentInfo.getPaymentStatus().equals(PaymentStatus.PAID.name())) {
                    // 如果此时交易记录状态为关闭或者已支付
                    return "failure";
                }

                String totalAmount = paramMap.get("total_amount");
                BigDecimal amount = new BigDecimal(totalAmount);
                if (paymentInfo.getTotalAmount().compareTo(amount) == 0 && paymentInfo.getOutTradeNo().equals(outTradeNo)) {
                    // 价格相等并且支付宝业务编号相等时，正常支付成功，修改交易记录
                    paymentService.paySuccess(outTradeNo, PaymentType.ALIPAY.name(), paramMap);
                    return "success";
                }

                return "failure";
            } else {
                // 交易状态为成功或者结束时
                return "failure";
            }
        } else {
            // 验签未通过
            return "failure";
        }
    }

    @RequestMapping("refund/{orderId}")
    @ResponseBody
    public Result<Object> refund(@PathVariable(value = "orderId")Long orderId) {
        // 调用退款接口
        boolean flag = alipayService.refund(orderId);

        return Result.ok(flag);
    }

    @ApiOperation("查看是否有交易记录")
    @RequestMapping("checkPayment/{orderId}")
    @ResponseBody
    public Boolean checkPayment(@PathVariable Long orderId){
        // 调用查询接口
        return alipayService.checkPayment(orderId);
    }

    @GetMapping("getPaymentInfo/{outTradeNo}")
    @ResponseBody
    public PaymentInfo getPaymentInfo(@PathVariable String outTradeNo){
        return paymentService.getPaymentInfo(outTradeNo, PaymentType.ALIPAY.name());
    }

}
