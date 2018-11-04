package com.atguigu.gmall0416.order.controller;


import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.fastjson.JSON;
import com.atguigu.gmall0416.LoginRequire;
import com.atguigu.gmall0416.bean.*;
import com.atguigu.gmall0416.bean.enums.OrderStatus;
import com.atguigu.gmall0416.bean.enums.ProcessStatus;
import com.atguigu.gmall0416.service.CartService;
import com.atguigu.gmall0416.service.ManageService;
import com.atguigu.gmall0416.service.OrderService;
import com.atguigu.gmall0416.service.UserInfoService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
public class OrderController {

    // 在订单项目中，调用usermanage项目中 根据userId查询userAddress方法
    //@Autowired
    @Reference
    private UserInfoService userInfoService;

    /*
    @RequestMapping("trade")
    @ResponseBody
    public List<UserAddress> getUserAddress(String userId){

        List<UserAddress> userAddressList = userInfoService.getUserAddressList(userId);

        return userAddressList;
    }*/

    // private UserAddressService userAddressService;

    @Reference
    private CartService cartService;

    @Reference
    private OrderService orderService;

    @Reference
    private ManageService manageService;

    @RequestMapping("trade")
    @LoginRequire
    public String trade(HttpServletRequest request){

        // 被选中的商品 redis user:userId:checked
        String userId = (String) request.getAttribute("userId");
        // 根据用户Id获取用户的收货地址，一个人可能有很多个收货地址，所有返回一个地址集合
        List<UserAddress> userAddressList = userInfoService.getUserAddressList(userId);

        // 循环选中商品列表
        List<CartInfo> cartInfoList =  cartService.getCartCheckedList(userId);
        // 将cartInfo 的集合 循环遍历给订单详情表
        ArrayList<OrderDetail> orderDetailList = new ArrayList<>(cartInfoList.size());
        for (CartInfo cartInfo : cartInfoList) {
            OrderDetail orderDetail = new OrderDetail();
            // 属性赋值
            orderDetail.setSkuId(cartInfo.getSkuId());
            orderDetail.setSkuName(cartInfo.getSkuName());
            orderDetail.setImgUrl(cartInfo.getImgUrl());
            orderDetail.setSkuNum(cartInfo.getSkuNum());
            orderDetail.setOrderPrice(cartInfo.getCartPrice());
            orderDetailList.add(orderDetail);
        }

        // 页面需要商品总金额：商品详情列表中的所有价格的总和
        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setOrderDetailList(orderDetailList);
        // 计算总价格  this.totalAmount=  totalAmount;
        orderInfo.sumTotalAmount();//这个方法把计算好的总价格赋给了当前的totalAmount

        request.setAttribute("totalAmount",orderInfo.getTotalAmount());//使用get方法就能得到总金额

        request.setAttribute("orderDetailList",orderDetailList);
        request.setAttribute("userAddressList",userAddressList);
        request.setAttribute("cartInfoList",cartInfoList);
        //将流水号存储
        request.setAttribute("tradeCode",orderService.getTradeNo(userId));

        return "trade";
    }

    @RequestMapping(value = "submitOrder",method = RequestMethod.POST)
    @LoginRequire
    public  String submitOrder(OrderInfo orderInfo,HttpServletRequest request){
        // 获取登录userId
        String userId = (String) request.getAttribute("userId");
        // 先获取页面传递过来的tradeCode
        String tradeNo = request.getParameter("tradeNo");
        // 做比较
        boolean flag = orderService.checkTradeCode(userId,tradeNo);
        if (!flag){
            request.setAttribute("errMsg","该页面已失效，请重新提交!");
            return "tradeFail";
        }

        // 验证库存！验证orderDetailList中每一个商品
        List<OrderDetail> orderDetailList = orderInfo.getOrderDetailList();
        if (orderDetailList!=null && orderDetailList.size()>0){

            // 遍历
            for (OrderDetail orderDetail : orderDetailList) {
                /*// 验价：cartInfo.cartPrice() == {cartInfo.skuPrice=skuInfo.price} 展示商品价格应该是skuInfo.price ,验价= cartPrice - skuInfo.price
                SkuInfo skuInfo = manageService.getSkuInfo(orderDetail.getSkuId());
                if (!orderDetail.getOrderPrice().equals(skuInfo.getPrice())){
                    request.setAttribute("errMsg","该商品价格有变动，请从新下单，请重新下单!");
                    return "tradeFail";
                }*/

                boolean result = orderService.checkStock(orderDetail.getSkuId(), orderDetail.getSkuNum());
                if (!result){
                    request.setAttribute("errMsg","库存不足，请重新下单!");
                    return "tradeFail";
                }
            }
        }


        // 设置订单状态，订单进程状态，总金额
        orderInfo.setOrderStatus(OrderStatus.UNPAID);
        orderInfo.setProcessStatus(ProcessStatus.UNPAID);
        orderInfo.sumTotalAmount();

        orderInfo.setUserId(userId);
        // 插入数据
        String orderId = orderService.saveOrder(orderInfo);
        // 删除redis 中的 tradeNo
        orderService.delTradeCode(userId);
        // 根据订单的id 进行支付
        return "redirect://payment.gmall.com/index?orderId="+orderId;
    }

    // 拆单控制器
    @RequestMapping("orderSplit")
    @ResponseBody
    public String orderSplit(HttpServletRequest request){
        // 库存系统发送支付成功之后，验证order。将orderId，以及wareSkuMap
        // orderInfo  --- OrderDetail
        String orderId = request.getParameter("orderId");
        // [{"wareId":"1","skuIds":["2","10"]},{"wareId":"2","skuIds":["3"]}]
        String wareSkuMap = request.getParameter("wareSkuMap");
        // 根据返回的结果调用服务层
        List<OrderInfo> orderInfoList =  orderService.splitOrder(orderId,wareSkuMap);
        // 声明一个返回的集合 orderInfo. 基本信息，以及orderDetail的基本信息
        List list = new ArrayList();
        for (OrderInfo orderInfo : orderInfoList) {
            Map map = orderService.initWareOrder(orderInfo);
            list.add(map);
        }
        String jsonOrderInfo = JSON.toJSONString(list);
        System.out.println(jsonOrderInfo);
        return jsonOrderInfo;
    }

}