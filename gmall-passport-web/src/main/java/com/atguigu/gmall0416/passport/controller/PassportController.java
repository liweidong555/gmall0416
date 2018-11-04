package com.atguigu.gmall0416.passport.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.atguigu.gmall0416.bean.UserInfo;
import com.atguigu.gmall0416.passport.util.JwtUtil;
import com.atguigu.gmall0416.service.UserInfoService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

@Controller
public class PassportController {

    @Value("${token.key}")
    private String key;

    //用户模块的service
    @Reference
    private UserInfoService userInfoService;

    @RequestMapping("index")
    public String index(HttpServletRequest request){
        //从表单中取得上一次跳来的url，完成登录后再回到那个页面
        String originUrl = request.getParameter("originUrl");
        //放到request中
        request.setAttribute("originUrl",originUrl);
        return "index";
    }

    @RequestMapping("login")
    @ResponseBody
    //想取得页面的用户名和密码，我们使用对象传值-用户表(UserInfo)
    public String login(UserInfo userInfo,HttpServletRequest request){
        // 获取linux服务器的ip=192.168.83.1
        String ip = request.getHeader("X-forwarded-for");
        //用户名+密码进行验证
        UserInfo loginUser = userInfoService.login(userInfo);
        if(loginUser!=null){
            // 做token -- JWT
            HashMap<String, Object> map = new HashMap<>();
            map.put("userId",loginUser.getId());
            map.put("nickName",loginUser.getNickName());

            String token = JwtUtil.encode(key, map, ip);
            return token;
        }else {
            return "fail";
        }
    }

    // 登录认证方法
    @RequestMapping("verify")
    @ResponseBody
    public String verify(HttpServletRequest request){
        // 取得token
        String  token = request.getParameter("token");
        //  salt = ip
        String salt = request.getHeader("X-forwarded-for");
        // 对token进行解密 {userId=1001, nickName=admin}
        Map<String, Object> map = JwtUtil.decode(token, key, salt);

        // map 中的userId 。跟redis 中进行匹配。
        if (map!=null && map.size()>0){
            String userId = (String) map.get("userId");
            // 调用认证方法将userId 传入进去
            UserInfo userInfo =  userInfoService.verify(userId);
            if (userInfo!=null){
                return "success";
            }else {
                return "fail";
            }
        }
        return "fail";

    }
}
