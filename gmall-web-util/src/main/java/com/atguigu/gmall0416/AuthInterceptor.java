package com.atguigu.gmall0416;

import com.alibaba.fastjson.JSON;
import io.jsonwebtoken.impl.Base64UrlCodec;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Map;

// 实现，继承 被spring 容器扫描
@Component
public class AuthInterceptor extends HandlerInterceptorAdapter {
    // preHandle 进入控制器之前。
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 将生产token 放入cookie 中
        //http://item.gmall.com/30.html?newToken=eyJhbGciOiJIUzI1NiJ9.eyJuaWNrTmFtZSI6IkFkbWluaXN0cmF0b3IiLCJ1c2VySWQiOiIyIn0.QJcmrgjwtEps16xz5D_GMJMi_abEQJucT3oTrywWAPA
        String token = request.getParameter("newToken");
        if (token!=null){
            // 将token 放入cookie 中
            CookieUtil.setCookie(request,response,"token",token,WebConst.COOKIE_MAXAGE,false);
        }
        // 直接访问登录页面，当用户进入其他项目模块中。
        if (token==null){
            //  如果用户登录了，访问其他页面的时候不会有newToken，那么token 可能已经在cookie 中存在了
            token = CookieUtil.getCookieValue(request,"token",false);
        }
        // 已经登录的token，cookie 中的token。
        if (token!=null){
            // 去token 中的是有效数据，解密
            Map map = getUserMapByToken(token);
            String nickName = (String) map.get("nickName");
            request.setAttribute("nickName", nickName);
        }
        // Object handler
        // 获取方法，获取方法上的注解
        HandlerMethod handlerMethod = (HandlerMethod) handler;
        LoginRequire methodAnnotation = handlerMethod.getMethodAnnotation(LoginRequire.class);
        // 说明类上有注解！
        if (methodAnnotation!=null){
            //  必须要登录【调用认证】
            String remoteAddr = request.getHeader("x-forwarded-for");
            // 认证控制器在那个项目？ 远程调用，
            String result = HttpClientUtil.doGet(WebConst.VERIFY_ADDRESS + "?token=" + token + "&currentIp=" + remoteAddr);
            if ("success".equals(result)){
                // 说明当前用户已经登录，保存userId : 购物车使用！
                Map map = getUserMapByToken(token);
                String userId = (String) map.get("userId");
                request.setAttribute("userId", userId);
                return true;
            } else {
                // fail
                if (methodAnnotation.autoRedirect()){
                    // 认证失败！重新登录！
                    /*http://passport.atguigu.com/index?originUrl=http%3A%2F%2Fitem.gmall.com%2F30.html*/
                    String requestURL  = request.getRequestURL().toString(); // http://item.gmall.com/28.html
                    // 进行加密编码
                    String encodeURL = URLEncoder.encode(requestURL, "UTF-8");
                    response.sendRedirect(WebConst.LOGIN_ADDRESS+"?originUrl="+encodeURL);
                    return false;
                }
            }
        }
        return true;
    }
    // 解密
    private Map getUserMapByToken(String token) {
        //eyJhbGciOiJIUzI1NiJ9.eyJuaWNrTmFtZSI6Im1hcnJ5IiwidXNlcklkIjoiMTAwMSJ9.6FX9P5vzUb5oBZwrY5whK3NB3aGE51k6ot9o-0dVAHU
        String tokenUserInfo = StringUtils.substringBetween(token, ".");
        // 使用了另一个类
        Base64UrlCodec base64UrlCodec = new Base64UrlCodec();
        byte[] bytes = base64UrlCodec.decode(tokenUserInfo);

        // 数组-- map
        // 字符串
        String str = null;
        try {
            str =  new String(bytes ,"UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        // 字符串--map
        Map map = JSON.parseObject(str, Map.class);
        return map;
    }

}
