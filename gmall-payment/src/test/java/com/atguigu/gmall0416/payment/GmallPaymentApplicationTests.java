package com.atguigu.gmall0416.payment;

import com.atguigu.gmall0416.config.ActiveMQUtil;
import org.apache.activemq.command.ActiveMQTextMessage;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.jms.*;

@RunWith(SpringRunner.class)
@SpringBootTest
public class GmallPaymentApplicationTests {

	@Autowired
	private ActiveMQUtil activeMQUtil;

	@Test
	public void contextLoads() {
	}

	@Test
	public void TestActiveMq() throws JMSException {
		Connection connection = activeMQUtil.getConnection();
		// 打开连接
		connection.start();
		// 需要消息队列，需要消息的提供者
		Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

		Queue queue = session.createQueue("TestActiveMq");
		MessageProducer producer = session.createProducer(queue);
		// 准备发送消息
		ActiveMQTextMessage activeMQTextMessage = new ActiveMQTextMessage();
		activeMQTextMessage.setText("Atguigu!");

		producer.send(activeMQTextMessage);

		// 关闭
		producer.close();
		session.close();
		connection.close();
	}

}
