package com.atguigu.gmall0416.item.controller;



import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.fastjson.JSON;
import com.atguigu.gmall0416.LoginRequire;
import com.atguigu.gmall0416.bean.SkuInfo;
import com.atguigu.gmall0416.bean.SkuSaleAttrValue;
import com.atguigu.gmall0416.bean.SpuSaleAttr;
import com.atguigu.gmall0416.service.ListService;
import com.atguigu.gmall0416.service.ManageService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;

@Controller
public class ItemController {

    @Reference
    private ManageService manageService;

    @Reference
    private ListService listService;

    @RequestMapping("{skuId}.html")
//    @LoginRequire(autoRedirect = true)
    public String index(@PathVariable(value = "skuId") String skuId, Model model , HttpServletRequest request){

        System.out.println("skuId="+skuId);
        //根据skuId查询表skuInfo，调用后台manageService
        SkuInfo skuInfo =  manageService.getSkuInfo(skuId);
        //存入信息，给前台使用
        request.setAttribute("skuInfo",skuInfo);

        //查询限售属性以及限售属性值
        List<SpuSaleAttr> spuSaleAttrList = manageService.selectSpuSaleAttrListCheckBySku(skuInfo);
        //根据skuId=skuInfo-spuId。因为spu下关联着限售属性，以及限售属性值
        //存入信息，给前台使用
        request.setAttribute("spuSaleAttrList",spuSaleAttrList);

        // 做拼接字符串的功能
        List<SkuSaleAttrValue> skuSaleAttrValueListBySpu = manageService.getSkuSaleAttrValueListBySpu(skuInfo.getSpuId());

//        # 108|110 30 108|110 32 109|111 31 == 在java代码中拼写。
//        String jsonKey = "";
//        HashMap map = new HashMap(); 5 0-4
//        map.put("108|110",30);
//        map.put("109|111",31);

        String jsonKey = "";
        HashMap<String, String> map = new HashMap<>();
        for (int i = 0; i < skuSaleAttrValueListBySpu.size(); i++) {
            // 取得集合中的数据
            SkuSaleAttrValue skuSaleAttrValue = skuSaleAttrValueListBySpu.get(i);
            if (jsonKey.length()!=0){
                jsonKey+="|";
            }
            // jsonKey+=108 jsonKey+=108|110
            jsonKey+=skuSaleAttrValue.getSaleAttrValueId();
            // 什么时候将jsonKey 重置！什么时候结束拼接
            if ((i+1)==skuSaleAttrValueListBySpu.size() || !skuSaleAttrValue.getSkuId().equals(skuSaleAttrValueListBySpu.get(i+1).getSkuId()) ){
                map.put(jsonKey,skuSaleAttrValue.getSkuId());
                jsonKey="";
            }
        }
        // 转换json字符串
        String valuesSkuJson = JSON.toJSONString(map);
        System.out.println("valuesSkuJson="+valuesSkuJson);
        request.setAttribute("valuesSkuJson",valuesSkuJson);

        // 调用热度排名
        listService.incrHotScore(skuId);
        return "item";
    }
}
