package com.atguigu.gmall0416.config;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;


//初始化JedisPool配置参数。
//从JedisPool中取得Jedis。
public class RedisUtil {

    //先整一个JedisPool了对象，需要JedisPoolConfig
    private JedisPool jedisPool;

    //初始化配置方法  //database:控制数据存储在哪个库中
    public  void  initJedisPool(String host,int port,int database){

        JedisPoolConfig jedisPoolConfig = new JedisPoolConfig();
        // 总数
        jedisPoolConfig.setMaxTotal(200);
        // 获取连接时等待的最大毫秒
        jedisPoolConfig.setMaxWaitMillis(10*1000);
        // 最少剩余数
        jedisPoolConfig.setMinIdle(10);
        // 如果到最大数，设置等待
        jedisPoolConfig.setBlockWhenExhausted(true);
        // 等待时间
        jedisPoolConfig.setMaxWaitMillis(2000);
        // 在获取连接时，检查是否有效
        jedisPoolConfig.setTestOnBorrow(true);

        //初始化JedisPool
        jedisPool = new  JedisPool(jedisPoolConfig,host,port,20*1000);

    }

    //新增一个方法获得Jedis
    public Jedis getJedis(){
        Jedis jedis = jedisPool.getResource();
        return jedis;
    }



}
