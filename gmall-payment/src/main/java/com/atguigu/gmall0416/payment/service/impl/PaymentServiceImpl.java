package com.atguigu.gmall0416.payment.service.impl;

import com.alibaba.dubbo.config.annotation.Service;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.atguigu.gmall0416.bean.PaymentInfo;
import com.atguigu.gmall0416.bean.enums.PaymentStatus;
import com.atguigu.gmall0416.config.ActiveMQUtil;
import com.atguigu.gmall0416.payment.mapper.PaymentInfoMapper;
import com.atguigu.gmall0416.service.PaymentService;
import org.apache.activemq.ScheduledMessage;
import org.apache.activemq.command.ActiveMQMapMessage;
import org.springframework.beans.factory.annotation.Autowired;
import tk.mybatis.mapper.entity.Example;

import javax.jms.*;

@Service
public class PaymentServiceImpl implements PaymentService{

    @Autowired
    private PaymentInfoMapper paymentInfoMapper;

    @Autowired
    private ActiveMQUtil activeMQUtil;

    @Autowired
    private AlipayClient alipayClient;

    @Override
    public void savePaymentInfo(PaymentInfo paymentInfo) {
        paymentInfoMapper.insertSelective(paymentInfo);
    }

    @Override
    public PaymentInfo getpaymentInfo(PaymentInfo paymentInfo) {
        return paymentInfoMapper.selectOne(paymentInfo);
    }

    @Override
    public void updatePaymentInfo(PaymentInfo paymentInfoUpd, String out_trade_no) {
        Example example = new Example(PaymentInfo.class);
        // 拼接条件update paymentInfo set paymentStatus = ? ,createTime = ? where out_trade_no = ?
        example.createCriteria().andEqualTo("outTradeNo",out_trade_no);
        paymentInfoMapper.updateByExampleSelective(paymentInfoUpd,example);

    }

    @Override
    public void sendPaymentResult(PaymentInfo paymentInfo, String result) {

        // 创建工厂
        Connection connection = activeMQUtil.getConnection();
        try {
            connection.start();
            // 创建session
            Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
            // 创建队列，提供者
            Queue payment_result_queue = session.createQueue("PAYMENT_RESULT_QUEUE");

            MessageProducer producer = session.createProducer(payment_result_queue);

            // 设置发送的消息 orderId,result
            ActiveMQMapMessage mapMessage = new ActiveMQMapMessage();
            mapMessage.setString("orderId",paymentInfo.getOrderId());
            mapMessage.setString("result",result);

            producer.send(mapMessage);
            // 必须提交
            session.commit();

            producer.close();
            session.close();
            connection.close();

        } catch (JMSException e) {
            e.printStackTrace();
        }
    }

    //查询(核对)支付结果
    @Override
    public boolean checkPayment(PaymentInfo paymentInfoQuery) {
        AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
        request.setBizContent("{" +
                "\"out_trade_no\":\""+paymentInfoQuery.getOutTradeNo()+"\"" +
                "  }");
        AlipayTradeQueryResponse response = null;
        try {
            response = alipayClient.execute(request);
        } catch (AlipayApiException e) {
            e.printStackTrace();
        }
        // out_trade_no 值与支付宝中的out_trade_no 一致
        if(response.isSuccess()){
            System.out.println("调用成功！");
            // 验证支付结果：
            if ("TRADE_SUCCESS".equals(response.getTradeStatus()) || "TRADE_FINISHED".equals(response.getTradeStatus())){
                // 验证完成之后，修改交易记录表中的数据
                // 改支付状态
                PaymentInfo paymentInfoUpd = new PaymentInfo();
                // 状态修改为 已支付！
                paymentInfoUpd.setPaymentStatus(PaymentStatus.PAID);
                updatePaymentInfo(paymentInfoUpd,paymentInfoQuery.getOutTradeNo());
                //
                sendPaymentResult(paymentInfoQuery,"success");
                return true;
            }else {
                return false;
            }
        } else {
            System.out.println("调用失败");
        }
        return false;
    }

    @Override
    public void sendDelayPaymentResult(String outTradeNo, int delaySec, int checkCount) {
        // 创建工厂连接
        Connection connection = activeMQUtil.getConnection();
        // 打开连接
        try {
            connection.start();
            // 创建session
            Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
            // 创建队列
            Queue payment_result_check_queue = session.createQueue("PAYMENT_RESULT_CHECK_QUEUE");//payment_result_check_queue
            // 创建提供者
            MessageProducer producer = session.createProducer(payment_result_check_queue);
            // 准备发送消息，创建发送消息的内容
            ActiveMQMapMessage mapMessage = new ActiveMQMapMessage();
            mapMessage.setString("outTradeNo",outTradeNo);
            mapMessage.setInt("delaySec",delaySec);
            mapMessage.setInt("checkCount",checkCount);

            // 发送之前 设置延时时间
            mapMessage.setLongProperty(ScheduledMessage.AMQ_SCHEDULED_DELAY,delaySec*1000);
            producer.send(mapMessage);

            session.commit();
            producer.close();
            session.close();
            connection.close();
        } catch (JMSException e) {
            e.printStackTrace();
        }
    }

    //关闭交易，记录信息
    @Override
    public void closePayment(String id) {
        // id 即为==orderId
        PaymentInfo paymentInfo = new PaymentInfo();
        // 根据orderId 查询当前的paymentInfo 对象
        //  PaymentInfo paymentInfoQuery = paymentInfoMapper.selectOne(paymentInfo);
        paymentInfo.setPaymentStatus(PaymentStatus.ClOSED);

        Example example = new Example(PaymentInfo.class);
        example.createCriteria().andEqualTo("orderId",id);
        // update paymentInfo set PaymentStatus = ？where orderId = id；
        paymentInfoMapper.updateByExampleSelective(paymentInfo,example);
    }
}
