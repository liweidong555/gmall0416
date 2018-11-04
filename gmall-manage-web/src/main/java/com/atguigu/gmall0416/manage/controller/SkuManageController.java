package com.atguigu.gmall0416.manage.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.atguigu.gmall0416.bean.SkuInfo;
import com.atguigu.gmall0416.bean.SpuImage;
import com.atguigu.gmall0416.bean.SpuSaleAttr;
import com.atguigu.gmall0416.service.ManageService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

@Controller
public class SkuManageController {

    @Reference
    private ManageService manageService;

    @RequestMapping("spuImageList")
    @ResponseBody
    public List<SpuImage> spuImageList(String spuId){

        return manageService.getSpuImageList(spuId);
    }


    @RequestMapping("spuSaleAttrList")
    @ResponseBody
    public List<SpuSaleAttr> spuSaleAttrList(String spuId){

        return manageService.getSpuSaleAttrList(spuId);
    }


    @RequestMapping(value = "saveSku",method = RequestMethod.POST)
    @ResponseBody
    public void saveSku(SkuInfo skuInfo){

        manageService.saveSku(skuInfo);
    }

}
