package com.atguigu.gmall0416.service;

import com.atguigu.gmall0416.bean.PaymentInfo;

public interface PaymentService {
    // 保存交易记录
    void  savePaymentInfo(PaymentInfo paymentInfo);
    // 通过对象查找paymentInfo
    PaymentInfo getpaymentInfo(PaymentInfo paymentInfo);
    // 更新方法
    void updatePaymentInfo(PaymentInfo paymentInfoUpd, String out_trade_no);
    //发送 订单状态 orderId , success;
    void sendPaymentResult(PaymentInfo paymentInfo,String result);
    //查询支付结果
    boolean checkPayment(PaymentInfo paymentInfoQuery);
    // outTradeNo ： checkPayment(); delaySec:多少秒，checkCount：查询次数
    void sendDelayPaymentResult(String outTradeNo,int delaySec ,int checkCount);
    //关闭交易，记录信息
    void closePayment(String id);
}
