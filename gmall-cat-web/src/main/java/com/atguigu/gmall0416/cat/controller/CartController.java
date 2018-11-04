package com.atguigu.gmall0416.cat.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.atguigu.gmall0416.LoginRequire;
import com.atguigu.gmall0416.bean.CartInfo;
import com.atguigu.gmall0416.bean.SkuInfo;
import com.atguigu.gmall0416.service.CartService;
import com.atguigu.gmall0416.service.ManageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;

@Controller
public class CartController {

    @Autowired
    private CartCookieHandler cartCookieHandler;

    @Reference
    private CartService cartService;

    @Reference
    private ManageService manageService;

    @RequestMapping("addToCart")
    @LoginRequire(autoRedirect = false)
    public String addToCart(HttpServletRequest request, HttpServletResponse response) {
        // 获取userId，skuId，skuNum
        String skuId = request.getParameter("skuId");
        String skuNum = request.getParameter("skuNum");

        String userId = (String) request.getAttribute("userId");
        // 判断用户是否登录
        if (userId != null) {
            // 说明用户登录,添加到数据库
            cartService.addToCart(skuId, userId, Integer.parseInt(skuNum));
        } else {
            // 说明用户没有登录,没有登录放到cookie中
            cartCookieHandler.addToCart(request, response, skuId, userId, Integer.parseInt(skuNum));
        }
        // 添加成功页面需要skuInfo对象
        SkuInfo skuInfo = manageService.getSkuInfo(skuId);
        request.setAttribute("skuInfo", skuInfo);
        request.setAttribute("skuNum", skuNum);
        return "success";
    }

    @RequestMapping("cartList")
    @LoginRequire(autoRedirect = false)
    public String cartList(HttpServletRequest request, HttpServletResponse response) {
        // 判断用户是否登录，登录了从redis中，redis中没有，从数据库中取
        String userId = (String) request.getAttribute("userId");
        if (userId != null) {
            // 从redis中取得，或者从数据库中
            List<CartInfo> cartInfoList = null;
            //会有合并的操作，cookie中的数据没有，应该删除！合并redis和cookie
            //现获取cookie中的数据
            List<CartInfo> cartListFromCookie = cartCookieHandler.getCartList(request);
            if(cartListFromCookie != null && cartListFromCookie.size() > 0){
                //写一个方法合并
                cartInfoList=cartService.mergeToCartList(cartListFromCookie,userId);
                // 删除cookie中的购物车
                cartCookieHandler.deleteCartCookie(request,response);

            }else{
                //cookie没有。直接从redis中获取
                cartInfoList = cartService.getCartList(userId);
            }
            request.setAttribute("cartInfoList",cartInfoList);
        } else {
            //cookie中单纯的查看，不做任何操作
            List<CartInfo> cartList = cartCookieHandler.getCartList(request);
            request.setAttribute("cartInfoList",cartList);

        }
        return "cartList";


    }
    //商品选中状态控制器
    @RequestMapping(value = "checkCart",method = RequestMethod.POST)
    @ResponseBody
    @LoginRequire(autoRedirect = false)
    public void checkCart(HttpServletRequest request,HttpServletResponse response){
        //  var param="isChecked="+isCheckedFlag+"&"+"skuId="+skuId;
        String isChecked = request.getParameter("isChecked");
        String skuId = request.getParameter("skuId");
        // 得到userId
        String userId = (String) request.getAttribute("userId");

        if ( userId!=null ){
            // 操作redis 登录，存值的时候需要userId
            cartService.checkCart(skuId,isChecked,userId);
        }else {
            // 操作cookie 不需要userId
            cartCookieHandler.checkCart(request,response,skuId,isChecked);
        }
    }
    //去结算
    @RequestMapping("toTrade")
    @LoginRequire(autoRedirect = true)
    public String toTrade(HttpServletRequest request,HttpServletResponse response){

        String userId = (String) request.getAttribute("userId");
        // 选中的商品列表，redis，cookie 中是不是也有可能有选中的！
        List<CartInfo> cookieHandlerCartList  = cartCookieHandler.getCartList(request);
        if (cookieHandlerCartList!=null && cookieHandlerCartList.size()>0){
            // 调用合并方法
            cartService.mergeToCartList(cookieHandlerCartList, userId);
            // 删除cookie数据
            cartCookieHandler.deleteCartCookie(request,response);
        }
        return "redirect://order.gmall.com/trade";
    }


}