package com.atguigu.gmall0416.usermanage.controller;


import com.atguigu.gmall0416.bean.UserInfo;
import com.atguigu.gmall0416.service.UserInfoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

@Controller
public class UserInfoController {
    @Autowired
    private UserInfoService userInfoService;

    @RequestMapping("findAll")
    @ResponseBody
    public List<UserInfo> getAll(){

        return userInfoService.findAll();
    }



}
