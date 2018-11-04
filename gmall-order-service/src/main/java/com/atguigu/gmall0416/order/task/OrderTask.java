package com.atguigu.gmall0416.order.task;

import com.alibaba.dubbo.config.annotation.Reference;
import com.atguigu.gmall0416.bean.OrderInfo;
import com.atguigu.gmall0416.service.OrderService;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

//在类上添加注解
@EnableScheduling
@Component
public class OrderTask {

    @Reference
    private OrderService orderService;

//    设置定时扫描 每分钟的第五秒
//    @Scheduled(cron = "5 * * * *  ?")
//    public  void sayHi(){
//        System.out.println(Thread.currentThread().getName()+"=======currentThread01");
//    }

//    // 设置定时扫描 每隔五秒执行
//    @Scheduled(cron = "0/5 * * * * ?")
//    public  void say(){
//        System.out.println(Thread.currentThread().getName()+"=======currentThread02");
//    }

    // 定时关闭过期的订单 -- 关闭订单7天！ -- 根据下单的成功率！
    @Scheduled(cron = "0/30 * * * * ?")
    public  void checkOrder(){
        // 关闭过期订单？什么是过期的订单 根据过期时间与当前时间，状态是未付款。
        List<OrderInfo> expiredOrderList =   orderService.getExpiredOrderList();
        // 循环遍历当前的集合
        for (OrderInfo orderInfo : expiredOrderList) {
            // 处理过期的每个订单
            orderService.execExpiredOrder(orderInfo);
        }
    }

}

