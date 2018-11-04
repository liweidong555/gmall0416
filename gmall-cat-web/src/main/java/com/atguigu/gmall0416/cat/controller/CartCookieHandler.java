package com.atguigu.gmall0416.cat.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.fastjson.JSON;
import com.atguigu.gmall0416.CookieUtil;
import com.atguigu.gmall0416.bean.CartInfo;
import com.atguigu.gmall0416.bean.SkuInfo;
import com.atguigu.gmall0416.service.ManageService;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.List;

//操作cookie中购物车数据  业务逻辑和redis一样
@Component
public class CartCookieHandler {

    // 定义购物车名称
    private String cookieCartName = "CART";
    // 设置cookie 过期时间
    private int COOKIE_CART_MAXAGE=7*24*3600;

    @Reference
    private ManageService manageService;
    //未登录添加
    public void addToCart(HttpServletRequest request, HttpServletResponse response, String skuId, String userId, Integer skuNum){
        //需要判断购物车中是否存在
        String cookieValue = CookieUtil.getCookieValue(request, cookieCartName, true);
        boolean ifExist=false;
        List<CartInfo> cartInfoList = new ArrayList<>();
        if(cookieValue!=null && cookieValue.length() > 0){
            //将字符串转换成对象，购物车里可能有多个商品，所以要返回购物列表
            cartInfoList = JSON.parseArray(cookieValue, CartInfo.class);
            //对集合进行循环遍历
            for (CartInfo cartInfo : cartInfoList) {
                if(cartInfo.getSkuId().equals(skuId)){
                    //数量+skuNum
                    cartInfo.setSkuNum(cartInfo.getSkuNum()+skuNum);
                    //从新放入cookie！
                    ifExist=true;
                }
            }
        }
        // //购物车里没有对应的商品 或者 没有购物车
        if (!ifExist){
            //把商品信息取出来，新增到购物车
            SkuInfo skuInfo = manageService.getSkuInfo(skuId);
            CartInfo cartInfo=new CartInfo();

            cartInfo.setSkuId(skuId);
            cartInfo.setCartPrice(skuInfo.getPrice());
            cartInfo.setSkuPrice(skuInfo.getPrice());
            cartInfo.setSkuName(skuInfo.getSkuName());
            cartInfo.setImgUrl(skuInfo.getSkuDefaultImg());

            cartInfo.setUserId(userId);
            cartInfo.setSkuNum(skuNum);
            cartInfoList.add(cartInfo);
        }

        // 把购物车写入cookie
        CookieUtil.setCookie(request,response,cookieCartName,JSON.toJSONString(cartInfoList),COOKIE_CART_MAXAGE,true);


    }

    public List<CartInfo> getCartList(HttpServletRequest request) {
        //直接从cookie中取值
        String cookieValue = CookieUtil.getCookieValue(request, cookieCartName, true);
        if(cookieValue != null && cookieValue.length() > 0){
            //cookieValue装换成集合对象
            List<CartInfo> cartInfoList = JSON.parseArray(cookieValue, CartInfo.class);
            return cartInfoList;
        }

        return null;
    }

    public void deleteCartCookie(HttpServletRequest request, HttpServletResponse response) {
        CookieUtil.deleteCookie(request,response,cookieCartName);
    }

    public  void  checkCart(HttpServletRequest request,HttpServletResponse response,String skuId,String isChecked){
        //  取出cookie中购物车所有的商品
        List<CartInfo> cartList = getCartList(request);
        if(cartList!=null && cartList.size()>0){
            // 循环比较
            for (CartInfo cartInfo : cartList) {
                //如果页面的传过来的商品Id和cookie的商品Id一样，我们就把页面商品的选中状态(isChecked)付给cookie的商品
                if (cartInfo.getSkuId().equals(skuId)){
                    cartInfo.setIsChecked(isChecked);
                }
            }
        }

        // 保存到cookie
        String newCartJson = JSON.toJSONString(cartList);
        CookieUtil.setCookie(request,response,cookieCartName,newCartJson,COOKIE_CART_MAXAGE,true);
    }

}
