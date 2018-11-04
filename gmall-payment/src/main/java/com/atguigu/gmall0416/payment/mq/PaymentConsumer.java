package com.atguigu.gmall0416.payment.mq;

import com.alibaba.dubbo.config.annotation.Reference;
import com.atguigu.gmall0416.bean.PaymentInfo;
import com.atguigu.gmall0416.bean.enums.ProcessStatus;
import com.atguigu.gmall0416.service.PaymentService;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

import javax.jms.JMSException;
import javax.jms.MapMessage;

@Component
public class PaymentConsumer {

    @Reference
    private PaymentService paymentService;

    @JmsListener(destination = "PAYMENT_RESULT_CHECK_QUEUE",containerFactory = "jmsQueueListener")
    public void  consumerPaymentResult(MapMessage mapMessage) throws JMSException {
        // 获取消息队列中的值
        String outTradeNo = mapMessage.getString("outTradeNo");
        int delaySec = mapMessage.getInt("delaySec");
        int checkCount = mapMessage.getInt("checkCount");
        // 调用查询支付是否成功的方法
        // outTradeNo 赋给对象
        PaymentInfo paymentInfo = new PaymentInfo();
        paymentInfo.setOutTradeNo(outTradeNo);
        // 验证 ，如果是false ,表示没有付款！true 则已经付款
        boolean flag = paymentService.checkPayment(paymentInfo);
        if (!flag && checkCount>0){
            // 从新发送消息
            System.out.println("checkCount============"+checkCount);
            // -- 在后先用后减
            paymentService.sendDelayPaymentResult(outTradeNo, delaySec,  checkCount-1);
        }else {
            // 通过outTradeNo 查询一个有orderId 的对象
            PaymentInfo paymentInfoQuery = new PaymentInfo();
            paymentInfoQuery.setOutTradeNo(outTradeNo);
            PaymentInfo paymentInfoNew = paymentService.getpaymentInfo(paymentInfoQuery);
            paymentService.sendPaymentResult(paymentInfoNew,"success");
        }
    }
}
