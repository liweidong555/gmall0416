package com.atguigu.gmall0416.cart.service.impl;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.dubbo.config.annotation.Service;
import com.alibaba.fastjson.JSON;
import com.atguigu.gmall0416.bean.CartInfo;
import com.atguigu.gmall0416.bean.SkuInfo;
import com.atguigu.gmall0416.cart.constant.CartConst;
import com.atguigu.gmall0416.cart.mapper.CartInfoMapper;
import com.atguigu.gmall0416.config.RedisUtil;
import com.atguigu.gmall0416.service.CartService;
import com.atguigu.gmall0416.service.ManageService;
import org.springframework.beans.factory.annotation.Autowired;
import redis.clients.jedis.Jedis;

import java.util.*;


@Service
public class CartServiceImpl implements CartService{

    @Autowired
    private CartService cartService;

    @Autowired
    private CartInfoMapper cartInfoMapper;

    @Autowired
    private RedisUtil redisUtil;

    @Reference
    private ManageService manageService;

    @Override
    public void addToCart(String skuId, String userId, Integer skuNum) {
        //先判断购物车中是否有该商品 - skuId取得数据
        CartInfo cartInfo = new CartInfo();
        cartInfo.setSkuId(skuId);
        cartInfo.setUserId(userId);
        //查询一下 select * from cart_info where skuId=? and userId=?
        CartInfo cartInfoExist = cartInfoMapper.selectOne(cartInfo);
        //如果有商品，数量+1
        if(cartInfoExist!=null){
            //数量+skuNum
            cartInfoExist.setSkuNum(cartInfoExist.getSkuNum()+skuNum);
            //更新数据库
            cartInfoMapper.updateByPrimaryKeySelective(cartInfoExist);
        }else{
           //没有商品，新增
           //CartInfo中所有的信息来自于SkuInfo
            SkuInfo skuInfo = manageService.getSkuInfo(skuId);
            CartInfo cartInfo1 = new CartInfo();
            cartInfo1.setSkuId(skuId);
            cartInfo1.setCartPrice(skuInfo.getPrice());
            cartInfo1.setSkuPrice(skuInfo.getPrice());
            cartInfo1.setSkuName(skuInfo.getSkuName());
            cartInfo1.setImgUrl(skuInfo.getSkuDefaultImg());
            cartInfo1.setUserId(userId);
            cartInfo1.setSkuNum(skuNum);
            // 插入数据库
            cartInfoMapper.insertSelective(cartInfo1);
            //修改缓存
            cartInfoExist = cartInfo1;
        }
        //放入缓存
        Jedis jedis = redisUtil.getJedis();
        //放入数据
        // 构建key user:userid:cart
        String userCartKey = CartConst.USER_KEY_PREFIX+userId+CartConst.USER_CART_KEY_SUFFIX;
        // 将对象序列化
        String cartInfoJson = JSON.toJSONString(cartInfoExist);
        //hset(key,field,value) key-user:userId:cart field-skuId value-cartInfo
        jedis.hset(userCartKey,skuId,cartInfoJson);

        // 更新购物车过期时间
        // user:userid:info
        String userInfoKey = CartConst.USER_KEY_PREFIX+userId+CartConst.USERINFOKEY_SUFFIX;
        //redis怎么获取key的过期时间ttl(key)
        Long ttl = jedis.ttl(userInfoKey);
        jedis.expire(userCartKey,ttl.intValue());
        jedis.close();

    }

    @Override
    public List<CartInfo> getCartList(String userId) {
        // 1.从redis 中取得数据
        Jedis jedis = redisUtil.getJedis();
        // key：user:userId:cart
        String userCartKey = CartConst.USER_KEY_PREFIX+userId+CartConst.USER_CART_KEY_SUFFIX;
        //String cartJson = jedis.hget(userCartKey,userId);
        //hash : 取值方式：将hash中的所有值一次性全部出去哪个方法？
        List<String> cartJsons  = jedis.hvals(userCartKey);

        List<CartInfo> cartInfoList = new ArrayList<>();
        // 判断遍历
        if (cartJsons!=null && cartJsons.size()>0){
            for (String cartJson : cartJsons) {
                // cartJson 对应的是每一个skuId 的值， 将cartJson 转换成我们的cartInfo对象
                CartInfo cartInfo = JSON.parseObject(cartJson, CartInfo.class);
                cartInfoList.add(cartInfo);
            }
            // 做一个排序
            cartInfoList.sort(new Comparator<CartInfo>() {
                @Override
                public int compare(CartInfo o1, CartInfo o2) {
                    // o1.getId()在bean中为String类型
                    // String 分类常用 6 length(); equals(); indexOf(); lastIndexOf();trim(): subString();
                    // String中 compareTo()比较大小;
                    return o1.getId().compareTo(o2.getId());
                }
            });
            return cartInfoList;
        }else {
            // 缓存中没有数据 --- db：
            List<CartInfo> cartInfoListDB = loadCartCache(userId);
            return cartInfoListDB;
        }
    }

    @Override
    public List<CartInfo> mergeToCartList(List<CartInfo> cartListFromCookie, String userId) {
        //现获取redis中的数据
        List<CartInfo> cartInfoList = cartInfoMapper.selectCartListWithCurPrice(userId);

        //cartListFromCookie 循环匹配[skuId]有同样的商品数量相加
        for (CartInfo cartInfoCK : cartListFromCookie) {
            boolean isMatch = false;
            for (CartInfo cartInfoDB : cartInfoList) {
                if(cartInfoCK.getSkuId().equals(cartInfoDB.getSkuId())){
                    cartInfoDB.setSkuNum(cartInfoDB.getSkuNum()+cartInfoCK.getSkuNum());
                    //更新数据库
                    cartInfoMapper.updateByPrimaryKeySelective(cartInfoDB);
                    isMatch = true;
                }
            }
            //不相等的情况 当前数据库中没有和cookie中相同的商品,就将cookie中商品插入数据
            if(!isMatch){
                cartInfoCK.setUserId(userId);
                cartInfoMapper.insertSelective(cartInfoCK);
            }
        }
        //以上代码只是操作数据库，并没有把最新的数据放入redis中
        //以下的代码是根据当前的用户从新查询最新数据并保存到redis中
        List<CartInfo> infoList = loadCartCache(userId);

        // 上面只是合并redis和cookie的商品数量，并没有判断是否被勾选(isChecked) ？
        for (CartInfo cartInfoDB : infoList) {
            // 循环cookie
            for (CartInfo infoCK : cartListFromCookie) {
                // 有相同的商品
                if (cartInfoDB.getSkuId().equals(infoCK.getSkuId())){
                    // 判断cookie 中的商品是否选中，如何有选中的，更新数据库。
                    if ("1".equals(infoCK.getIsChecked())){
                        // 从新给db 对象赋值为选中状态.
                        cartInfoDB.setIsChecked("1");
                        // 从新调用一下checkCart,更新商品的勾选情况
                        checkCart(cartInfoDB.getSkuId(), infoCK.getIsChecked(), userId);
                    }
                }
            }
        }
        return infoList;
    }

    //保存商品的勾选情况
    @Override
    public void checkCart(String skuId, String isChecked, String userId) {
        // 改变之前所有购物车中商品的状态
        // 准备redis ，准备key
        Jedis jedis = redisUtil.getJedis();
        // user:userId:cart
        String userCartKey = CartConst.USER_KEY_PREFIX+userId+CartConst.USER_CART_KEY_SUFFIX;
        // 将redis 中的数据取出来 hset(key,field,value) hget(key,field);
        String cartJson  = jedis.hget(userCartKey, skuId);
        // 将redis 中的字符串转换成对象
        CartInfo cartInfo = JSON.parseObject(cartJson, CartInfo.class);
        if (cartInfo!=null){
            cartInfo.setIsChecked(isChecked);
        }
        // 将修改完成的对象从新给redis
        jedis.hset(userCartKey,skuId,JSON.toJSONString(cartInfo));


        // === 新创建一个key 用来存储被选中的商品
        // user:userId:checked
        String userCheckedKey = CartConst.USER_KEY_PREFIX + userId + CartConst.USER_CHECKED_KEY_SUFFIX;
        // 放入被选中的购物车商品
        if ("1".equals(isChecked)){
            // 选中则添加
            jedis.hset(userCheckedKey,skuId,JSON.toJSONString(cartInfo));
        }else {
            // 状态为0 ，没有选中，则删除
            jedis.hdel(userCheckedKey,skuId);
        }
        jedis.close();

    }
    //根据用户Id获取购物车被选中的商品集合
    @Override
    public List<CartInfo> getCartCheckedList(String userId) {

        List<CartInfo> newCartList = new ArrayList<>();
        // redis
        Jedis jedis = redisUtil.getJedis();
        // key
        String userCheckedKey = CartConst.USER_KEY_PREFIX + userId + CartConst.USER_CHECKED_KEY_SUFFIX;
        // 取得redis 中的所有数据
        List<String> cartCheckedList  = jedis.hvals(userCheckedKey);
        // 循环遍历被选中的商品列表
        for (String cartJson : cartCheckedList) {
            // cartJson 是每一个商品，将其字符串转换为对象
            CartInfo cartInfo = JSON.parseObject(cartJson, CartInfo.class);
            newCartList.add(cartInfo);
        }
        // 返回新的集合
        return newCartList;
    }

    private List<CartInfo> loadCartCache(String userId) {
        //根据userId查询cartInfo，由于商品可能降价或者涨价，所以购物车里商品价格可能不一致
        List<CartInfo> cartInfoList = cartInfoMapper.selectCartListWithCurPrice(userId);
        if (cartInfoList==null && cartInfoList.size()==0){
            return null;
        }
        //放入redis
        String userCartKey = CartConst.USER_KEY_PREFIX+userId+CartConst.USER_CART_KEY_SUFFIX;
        Jedis jedis = redisUtil.getJedis();
        //迭代 hset(key,feild,value) key都是userCartKey feild:skuId value:单个的cartInfo的字符创
        //购物车可能有很多种商品 所以把feild,value当成k.v迭代到Map集合中
        Map<String,String> map = new HashMap<>(cartInfoList.size());
        for (CartInfo cartInfo : cartInfoList) {
            String cartJson = JSON.toJSONString(cartInfo);
            // key 都是同一个，值会产生重复覆盖！
            map.put(cartInfo.getSkuId(),cartJson);
        }
        // 将java list - redis hash
        // hmset(key String,Map<String,String>)一次放入多个值
        jedis.hmset(userCartKey,map);

        jedis.close();

        return  cartInfoList;
    }



}
