package com.atguigu.gmall0416.payment.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.fastjson.JSON;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.atguigu.gmall0416.bean.OrderInfo;
import com.atguigu.gmall0416.bean.PaymentInfo;
import com.atguigu.gmall0416.bean.enums.PaymentStatus;
import com.atguigu.gmall0416.payment.config.AlipayConfig;
import com.atguigu.gmall0416.service.OrderService;
import com.atguigu.gmall0416.service.PaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Controller
public class PaymentController {

    @Reference
    private OrderService orderService;

    @Reference
    private PaymentService paymentService;

    @Autowired
    private AlipayClient alipayClient;

    @RequestMapping("index")
    public String index(HttpServletRequest request){
        // ${orderId}
        String orderId = request.getParameter("orderId");
        request.setAttribute("orderId",orderId);
        // ${totalAmount}
        OrderInfo orderInfo =  orderService.getOrderInfo(orderId);
        request.setAttribute("totalAmount",orderInfo.getTotalAmount());
        return "index";
    }

    @RequestMapping(value = "alipay/submit",method = RequestMethod.POST)
    @ResponseBody
    public String submitPayment(HttpServletRequest request, HttpServletResponse response) {

        // 保存信息 根据orderId 进行查找数据保存paymentInfo表中
        String orderId = request.getParameter("orderId");
        OrderInfo orderInfo = orderService.getOrderInfo(orderId);

        // paymentInfo
        PaymentInfo paymentInfo = new PaymentInfo();
        paymentInfo.setOrderId(orderId);
        paymentInfo.setOutTradeNo(orderInfo.getOutTradeNo());
        paymentInfo.setTotalAmount(orderInfo.getTotalAmount());
        paymentInfo.setSubject("新一轮的测试！");
        paymentInfo.setPaymentStatus(PaymentStatus.UNPAID);
        // 保存交易记录
        paymentService.savePaymentInfo(paymentInfo);

        //生成一个支付的二维码
        // 做生成一个支付的二维码
        AlipayTradePagePayRequest alipayRequest = new AlipayTradePagePayRequest();//创建API对应的request
        // 配置类，工具类。
        alipayRequest.setReturnUrl(AlipayConfig.return_payment_url);
        alipayRequest.setNotifyUrl(AlipayConfig.notify_payment_url);//在公共参数中设置回跳和通知地址
        Map<String,Object> bizContnetMap=new HashMap<>();
        /*第三方交易编号*/
        bizContnetMap.put("out_trade_no",paymentInfo.getOutTradeNo());
        bizContnetMap.put("product_code","FAST_INSTANT_TRADE_PAY");//fast_instant_trade_pay
        bizContnetMap.put("subject",paymentInfo.getSubject());
        bizContnetMap.put("total_amount",paymentInfo.getTotalAmount());

        // 封装的参数map 集合转换成字符串
        String mapJson = JSON.toJSONString(bizContnetMap);

        alipayRequest.setBizContent(mapJson);
        String form="";
        try {
            form = alipayClient.pageExecute(alipayRequest).getBody(); //调用SDK生成表单
        } catch (AlipayApiException e) {
            e.printStackTrace();
        }

        response.setContentType("text/html;charset=UTF-8");
        paymentService.sendDelayPaymentResult(paymentInfo.getOutTradeNo(),15, 3);
        return form; // 页面
    }

    // 支付宝同步回调接口
    @RequestMapping("alipay/callback/return")
    public String callbackReturn(){
        return "redirect://"+AlipayConfig.return_order_url;
    }

    // 支付宝异步回调接口 : 通知电商系统支付到底是成功，还是失败！
    @RequestMapping("/alipay/callback/notify")
    @ResponseBody
    // springMVC注解@RequestParam
    public String paymentNotify(@RequestParam Map<String,String> paramMap, HttpServletRequest request) throws AlipayApiException {

        // 在得知成功的情况下，更新订单的状态： 前提：交易状态TRADE_SUCCESS，TRADE_FINISHED  finisthed
        boolean signVerified = AlipaySignature.rsaCheckV1(paramMap, AlipayConfig.alipay_public_key,"utf-8",AlipayConfig.sign_type); //调用SDK验证签名

        String trade_status = paramMap.get("trade_status");

        if(signVerified){
            //  TODO 验签成功后，按照支付结果异步通知中的描述，对支付结果中的业务内容进行二次校验，校验成功后在response中返回success并继续商户自身业务处理，校验失败返回failure
            // 当前的订单，已经关闭或者是已经完结！交易失败!

            // 利用第三方交易编号
            String out_trade_no = paramMap.get("out_trade_no");
            // 通过out_trade_no 查询当前的paymentInfo 的状态
            PaymentInfo paymentInfo = new PaymentInfo();
            paymentInfo.setOutTradeNo(out_trade_no);
            // select * from paymentInfo where out_trade_no = ?
            PaymentInfo paymentInfoHas = paymentService.getpaymentInfo(paymentInfo);
            //
            if (paymentInfoHas.getPaymentStatus()==PaymentStatus.PAID || paymentInfoHas.getPaymentStatus()==PaymentStatus.ClOSED){
                return "fail";
            }else {
                //  前提：交易状态TRADE_SUCCESS，TRADE_FINISHED
                if ("TRADE_SUCCESS".equals(trade_status) || "TRADE_FINISHED".equals(trade_status)){
                    //  状态成功之后，才去修改订单的状态！动db。orderInfo，paymentInfo

                    PaymentInfo paymentInfoUpd = new PaymentInfo();
                    paymentInfoUpd.setPaymentStatus(PaymentStatus.PAID);
                    paymentInfoUpd.setCreateTime(new Date());
                    paymentInfoUpd.setSubject(paramMap.toString());
                    paymentInfoUpd.setCallbackTime(new Date());
                    // update paymentInfo set paymentStatus = ? ,createTime = ? where out_trade_no = ?
                    paymentService.updatePaymentInfo(paymentInfoUpd,out_trade_no);
                    // 交易成功！
                    // 发送通知:orderId,success/fail
                    paymentService.sendPaymentResult(paymentInfoUpd,"success");
                    return "success";
                } else {
                    paymentService.sendPaymentResult(paymentInfo,"fail");
                    return "fail";
                }
            }
        }else{
            // TODO 验签失败则记录异常日志，并在response中返回failure.
            return "fail";
        }
    }

    @RequestMapping("sendPaymentResult")
    @ResponseBody
    public String sendPaymentResult(PaymentInfo paymentInfo,String result){
        paymentService.sendPaymentResult(paymentInfo,result);
        return "sendPaymentResult";
    }

    @RequestMapping("queryPaymentResult")
    @ResponseBody
    public String queryPaymentResult(PaymentInfo paymentInfo){
        PaymentInfo paymentInfoQuery = paymentService.getpaymentInfo(paymentInfo);
        // paymentInfoQuery.outTradeNo
        boolean result = paymentService.checkPayment(paymentInfoQuery);
        return ""+result;
    }
}
