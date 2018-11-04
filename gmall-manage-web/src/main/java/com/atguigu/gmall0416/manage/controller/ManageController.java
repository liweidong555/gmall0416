package com.atguigu.gmall0416.manage.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class ManageController {
    @RequestMapping("index")
    public  String index(){
        return "index";
    }

    @RequestMapping("attrListPage")
    public  String attrListPage(){
        return "attrListPage";
    }
}
