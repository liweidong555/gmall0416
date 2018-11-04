package com.atguigu.gmall0416.passport;

import com.atguigu.gmall0416.passport.util.JwtUtil;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.HashMap;
import java.util.Map;

@RunWith(SpringRunner.class)
@SpringBootTest
public class GmallPassportWebApplicationTests {

	@Test
	public void contextLoads() {
	}

	@Test
	public void test01(){
		String key = "atguigu";
		// ip = linux
		String ip="192.168.83.128";

		Map map = new HashMap();
		map.put("userId","1001");
		map.put("nickName","marry");
		// 调用加密算法
		String token = JwtUtil.encode(key, map, ip);
		//eyJhbGciOiJIUzI1NiJ9.eyJuaWNrTmFtZSI6Im1hcnJ5IiwidXNlcklkIjoiMTAwMSJ9.6FX9P5vzUb5oBZwrY5whK3NB3aGE51k6ot9o-0dVAHU===
		System.out.println(token+"===");
		// 解密 -- key + salt 必须一致！否则解密失败！

		Map<String, Object> decode = JwtUtil.decode(token, key, "192.168.83.128");
		//{nickName=marry, userId=1001}===
		System.out.println(decode+"===");
	}
}
