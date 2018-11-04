package com.atguigu.gmall0416.order.service.impl;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.dubbo.config.annotation.Service;
import com.alibaba.fastjson.JSON;
import com.atguigu.gmall0416.bean.OrderDetail;
import com.atguigu.gmall0416.bean.OrderInfo;
import com.atguigu.gmall0416.bean.enums.ProcessStatus;
import com.atguigu.gmall0416.config.ActiveMQUtil;
import com.atguigu.gmall0416.config.RedisUtil;
import com.atguigu.gmall0416.order.mapper.OrderDetailMapper;
import com.atguigu.gmall0416.order.mapper.OrderInfoMapper;
import com.atguigu.gmall0416.service.OrderService;
import com.atguigu.gmall0416.service.PaymentService;
import com.atguigu.gmall0416.util.HttpClientUtil;
import org.apache.activemq.command.ActiveMQTextMessage;
import org.apache.commons.beanutils.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import redis.clients.jedis.Jedis;
import tk.mybatis.mapper.entity.Example;


import javax.jms.*;
import javax.jms.Queue;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

@Service
public class OrderServiceImpl implements OrderService{

    @Autowired
    private OrderInfoMapper orderInfoMapper;

    @Autowired
    private OrderDetailMapper orderDetailMapper;

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private ActiveMQUtil activeMQUtil;

    @Reference
    private PaymentService paymentService;

    @Override
    public String saveOrder(OrderInfo orderInfo) {
        // 订单的创建时间
        orderInfo.setCreateTime(new Date());
        // 过期时间
        Calendar calendar = Calendar.getInstance();
        // 当前时间+1
        calendar.add(Calendar.DATE,1);
        // 付一个过期时间
        orderInfo.setExpireTime(calendar.getTime());
        // 第三方交易编号
        String outTradeNo="ATGUIGU"+System.currentTimeMillis()+""+new Random().nextInt(1000);

        orderInfo.setOutTradeNo(outTradeNo);
        // 插入订单信息
        orderInfoMapper.insertSelective(orderInfo);
        // 订单的详细信息也要放入数据库
        List<OrderDetail> orderDetailList = orderInfo.getOrderDetailList();
        for (OrderDetail orderDetail : orderDetailList) {
            orderDetail.setOrderId(orderInfo.getId());
            orderDetailMapper.insertSelective(orderDetail);
        }
        return orderInfo.getId();
    }

    // 生成流水号
    public String getTradeNo(String userId){

        // 生成一个流水号：
        String tradeCode = UUID.randomUUID().toString();

        // 将tradeCode 保存到redis
        Jedis jedis = redisUtil.getJedis();

        // 起key：
        String tradeNoKey="user:"+userId+":tradeCode";
        // 存值
        jedis.setex(tradeNoKey,10*60,tradeCode);

        jedis.close();
        return tradeCode;
    }
    // 比较流水号
    public boolean checkTradeCode(String userId,String tradeCodeNo){
        // 取得redis - key
        String tradeNoKey="user:"+userId+":tradeCode";
        Jedis jedis = redisUtil.getJedis();
        String redisTradeCodeNo = jedis.get(tradeNoKey);
        if (tradeCodeNo.equals(redisTradeCodeNo)){
            return true;
        }else {
            return false;
        }
    }
    // 删除流水号
    public void delTradeCode(String userId){
        // 将tradeCode 保存到redis
        Jedis jedis = redisUtil.getJedis();
        // 起key：
        String tradeNoKey="user:"+userId+":tradeCode";
        jedis.del(tradeNoKey);
        jedis.close();
    }
    // 验证库存
    @Override
    public boolean checkStock(String skuId, Integer skuNum) {
        // 调用库存系统方法 远程调用：httpClient。
        String result = HttpClientUtil.doGet("http://www.gware.com/hasStock?skuId=" + skuId + "&num=" + skuNum);
        if ("1".equals(result)){
            return  true;
        }else {
            return false;
        }
    }

    @Override
    public OrderInfo getOrderInfo(String orderId) {
        // 需要根据orderId 查询orderDetail
        OrderDetail orderDetail = new OrderDetail();
        orderDetail.setOrderId(orderId);
        List<OrderDetail> orderDetails = orderDetailMapper.select(orderDetail);

        OrderInfo orderInfo = orderInfoMapper.selectByPrimaryKey(orderId);
        // 将查询出来的orderDetail集合放入orderInfo中
        orderInfo.setOrderDetailList(orderDetails);
        return orderInfo;
    }

    @Override
    public void updateOrderStatus(String orderId, ProcessStatus paid) {

        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setId(orderId);
        orderInfo.setProcessStatus(paid);
        // 修改订单的状态
        orderInfo.setOrderStatus(paid.getOrderStatus());

        orderInfoMapper.updateByPrimaryKeySelective(orderInfo);
    }

    @Override
    public void sendOrderStatus(String orderId) {

        // 创建工厂
        Connection connection = activeMQUtil.getConnection();
        // 得到Json字符串
        String orderJson = initWareOrder(orderId);
        // 打开工厂
        try {
            connection.start();
            // 创建session
            Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
            // 创建队列
            Queue order_result_queue = session.createQueue("ORDER_RESULT_QUEUE");
            // 创建提供者
            MessageProducer producer = session.createProducer(order_result_queue);

            ActiveMQTextMessage activeMQTextMessage = new ActiveMQTextMessage();
            // 得到的字符串：有orderInfo的基本信息以及OrderDetail详细信息 组成的json字符串
            // 将上述数据封装成一个map对象，使用fastJson 将map 转换为一个字符串即可！
            activeMQTextMessage.setText(orderJson);
            producer.send(activeMQTextMessage);
            session.commit();

            // 关闭操作
            producer.close();
            session.close();
            connection.close();
        } catch (JMSException e) {
            e.printStackTrace();
        }
    }

    //查询过期的订单
    @Override
    public List<OrderInfo> getExpiredOrderList() {
        Example example = new Example(OrderInfo.class);
        // 实体类的属性名 过期时间与状态一定是And 关系！
        example.createCriteria().andEqualTo("processStatus",ProcessStatus.UNPAID).andLessThan("expireTime",new Date());
        List<OrderInfo> orderInfos = orderInfoMapper.selectByExample(example);
        return orderInfos;
    }
    // 处理过期的每个订单
    @Override
    @Async
    public void execExpiredOrder(OrderInfo orderInfo) {
        //  关闭订单状态
        updateOrderStatus(orderInfo.getId(),ProcessStatus.CLOSED);
        // 关闭付款信息
        paymentService.closePayment(orderInfo.getId());
    }


    private String initWareOrder(String orderId) {
        OrderInfo orderInfo = getOrderInfo(orderId);
        Map map = initWareOrder(orderInfo);
        // 将map转换为字符串
        return   JSON.toJSONString(map);
    }
    //在同一个类里叫重载
    public Map initWareOrder(OrderInfo orderInfo) {
        // 创建一个map对象
        Map map = new HashMap<>();
        map.put("orderId",orderInfo.getId());

        map.put("consignee", orderInfo.getConsignee());
        map.put("consigneeTel",orderInfo.getConsigneeTel());
        map.put("orderComment",orderInfo.getOrderComment());
        map.put("orderBody","发送消息给库存系统，减库存");
        map.put("deliveryAddress",orderInfo.getDeliveryAddress());
        map.put("paymentWay","2");
//      拆单需要使用仓库的Id
        map.put("wareId",orderInfo.getWareId());
        // details ：orderDetail
        List<OrderDetail> orderDetailList = orderInfo.getOrderDetailList();
        // 声明一个集合来存储订单详情信息
        ArrayList<Object> arrayList = new ArrayList<>();
        // 循环遍历
        for (OrderDetail orderDetail : orderDetailList) {
            Map newMap =   new HashMap<>();
            newMap.put("skuId",orderDetail.getSkuId());
            newMap.put("skuName",orderDetail.getSkuName());
            newMap.put("skuNum",orderDetail.getSkuNum());
            arrayList.add(newMap);
        }
        map.put("details",arrayList);

        return map;
    }
    //拆单
    @Override
    public List<OrderInfo> splitOrder(String orderId, String wareSkuMap) {
        List<OrderInfo> subOrderList = new ArrayList<>();

        // 先根据orderId 查询到原始的订单 orderInfo 中的数据
        OrderInfo orderInfoOrigin  = getOrderInfo(orderId);
        // wareSkuMap [{"wareId":"1","skuIds":["2","10"]},{"wareId":"2","skuIds":["3"]}] 看仓库Id，里面的skuIds
        List<Map> maps = JSON.parseArray(wareSkuMap, Map.class);
        // 循环maps 取得到里面的值wareId，skuIds
        for (Map map : maps) {
            List<String> skuIds = (List<String>) map.get("skuIds");
            String wareId = (String) map.get("wareId");
            // 将主订单，拆分：两个子订单:分别有父订单的Id ，以及相应信息！
            OrderInfo subOrderInfo = new OrderInfo();
            // 属性拷贝
            try {
                BeanUtils.copyProperties(subOrderInfo,orderInfoOrigin);
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            }
            // 将主键置空
            subOrderInfo.setId(null);
            // 仓库Id
            subOrderInfo.setWareId(wareId);
            // 父订单Id
            subOrderInfo.setParentOrderId(orderInfoOrigin.getId());
            // 先有子订单的明细：子订单明细从原始订单明细中得来
            List<OrderDetail> orderDetailList = orderInfoOrigin.getOrderDetailList();

            // 声明一个子订单的明细集合
            List<OrderDetail> subOrderDetailList = new ArrayList<>();
            for (OrderDetail orderDetail : orderDetailList) {
                // 循环库存系统传递过来的仓库明细的Id 如果相等，则直接赋值
                for (String skuId : skuIds) {
                    if (skuId.equals(orderDetail.getSkuId())){
                        orderDetail.setId(null);
                        subOrderDetailList.add(orderDetail);
                    }
                }
            }
            // 将子订单的明细给子订单
            subOrderInfo.setOrderDetailList(subOrderDetailList);
            // 价格
            subOrderInfo.sumTotalAmount();
            //  保存
            saveOrder(subOrderInfo);
            // 将一个子订单添加到集合中
            subOrderList.add(subOrderInfo);
        }
        // 将订单的状态变为拆分状态
        updateOrderStatus(orderId,ProcessStatus.SPLIT);

        return subOrderList;
    }



}
