package com.atguigu.gmall0416.payment.mq;

import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.ActiveMQConnectionFactory;

import javax.jms.*;

public class ConsumerTest {
    //消费者
    public static void main(String[] args) throws JMSException{
        // 创建连接工厂
        ActiveMQConnectionFactory activeMQConnectionFactory = new ActiveMQConnectionFactory(ActiveMQConnection.DEFAULT_USER, ActiveMQConnection.DEFAULT_PASSWORD,"tcp://192.168.83.128:61616");
        //创建连接
        Connection connection = activeMQConnectionFactory.createConnection();
        //打开连接
        connection.start();
        //创建会话
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        Queue queue = session.createQueue("Atguigu");
        // 创建消费者
        MessageConsumer consumer = session.createConsumer(queue);
        // 监听消息
        consumer.setMessageListener(new MessageListener() {
            @Override
            public void onMessage(Message message) {
                // 参数就是收到的消息
                if (message instanceof MapMessage){
                    String text = null;
                    try {
                        text = ((MapMessage) message).getString("result");
                        text += ((MapMessage) message).getString("ordId");
                        System.out.println(text+"接受的消息");
                    } catch (JMSException e) {
                        e.printStackTrace();
                    }

                }
            }
        });


    }
}
