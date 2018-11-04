package com.atguigu.gmall0416.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
//spring中xxx.xml
@Configuration
public class RedisConfig {

    //通过配置文件的形式将host，port，database赋值 application.properties.
    //  disabled:表示一个默认值
    @Value("${spring.redis.host:disabled}")
    private String host;//如果配置文件中没有spring.redis.host,则host为disabled

    @Value("${spring.redis.port:0}")
    private int port;

    @Value("${spring.redis.database:0}")
    private int database;

    //在application.xml <bean class="com.atguigu.gmall0416.config.RedisConfig"> </bean>
    @Bean
    public RedisUtil getRedisUtil(){
        if(host.equals("disabled")){
            return null;
        }
        RedisUtil redisUtil=new RedisUtil();
        redisUtil.initJedisPool(host,port,database);
        return redisUtil;
    }


}
