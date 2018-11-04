package com.atguigu.gmall0416.order.mq;

import com.alibaba.dubbo.config.annotation.Reference;
import com.atguigu.gmall0416.bean.enums.ProcessStatus;
import com.atguigu.gmall0416.service.OrderService;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

import javax.jms.JMSException;
import javax.jms.MapMessage;

// 消费消息
@Component
public class OrderConsumer {
    @Reference
    private OrderService orderService;

    @JmsListener(destination = "PAYMENT_RESULT_QUEUE",containerFactory = "jmsQueueListener")
    public void  consumerPaymentResult(MapMessage mapMessage) throws JMSException {

        // 取得消息队列中的数据
        String orderId = mapMessage.getString("orderId");
        String result = mapMessage.getString("result");

        // 支付成功！
        if ("success".equals(result)){
            // 修改订单状态
            orderService.updateOrderStatus(orderId, ProcessStatus.PAID);
            // 通知库存系统减库存，修改订单的状态
            orderService.sendOrderStatus(orderId);
            orderService.updateOrderStatus(orderId,ProcessStatus.WAITING_DELEVER);//待发货
        }
    }


    @JmsListener(destination = "SKU_DEDUCT_QUEUE",containerFactory = "jmsQueueListener")
    public void  consumeSkuDeduct(MapMessage mapMessage) throws JMSException {

        // 取得消息队列中的数据
        String orderId = mapMessage.getString("orderId");
        String status = mapMessage.getString("status");

        // 减库存是否成功
        if ("DEDUCTED".equals(status)){
            // 修改订单状态
            orderService.updateOrderStatus(orderId, ProcessStatus.DELEVERED);//发货
        }
    }
}
