package com.atguigu.gmall0416.service;

import com.atguigu.gmall0416.bean.CartInfo;

import java.util.List;

public interface CartService {
    //
    void  addToCart(String skuId,String userId,Integer skuNum);

    List<CartInfo> getCartList(String userId);
    //cartListCK这是cookie里的数据
    List<CartInfo> mergeToCartList(List<CartInfo> cartListFromCookie, String userId);
    //商品选中状态
    void checkCart(String skuId, String isChecked, String userId);
    //获取redis中选中的商品列表
    List<CartInfo> getCartCheckedList(String userId);
}
