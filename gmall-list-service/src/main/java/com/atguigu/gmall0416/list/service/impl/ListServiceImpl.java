package com.atguigu.gmall0416.list.service.impl;

import com.alibaba.dubbo.config.annotation.Service;
import com.atguigu.gmall0416.bean.SkuLsInfo;
import com.atguigu.gmall0416.bean.SkuLsParams;
import com.atguigu.gmall0416.bean.SkuLsResult;

import com.atguigu.gmall0416.config.RedisUtil;
import com.atguigu.gmall0416.service.ListService;
import io.searchbox.client.JestClient;
import io.searchbox.core.*;
import io.searchbox.core.search.aggregation.MetricAggregation;
import io.searchbox.core.search.aggregation.TermsAggregation;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.TermsBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.highlight.HighlightBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import redis.clients.jedis.Jedis;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


@Service
public class ListServiceImpl implements ListService{
    //操作es的类
    @Autowired
    private JestClient jestClient;

    @Autowired
    private RedisUtil redisUtil;

    public static final String ES_INDEX="gmall";

    public static final String ES_TYPE="SkuInfo";


    @Override
    public void saveSkuInfo(SkuLsInfo skuLsInfo) {
        //保存到es 必须知道index，type
        //如何向es中添加数据，查询Search。Build(query)
        //PUT gmall/SkuInfo/1
        Index index = new Index.Builder(skuLsInfo).index(ES_INDEX).type(ES_TYPE).id(skuLsInfo.getId()).build();
        //执行操作
        try {
            DocumentResult result = jestClient.execute(index);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public SkuLsResult search(SkuLsParams skuLsParams) {
        // 先写dsl语句 ，使用java代码写
        String query=makeQueryStringForSearch(skuLsParams);
        // 执行
        Search search = new Search.Builder(query).addIndex(ES_INDEX).addType(ES_TYPE).build();
        SearchResult searchResult =null;
        try {
            // 执行的结果： searchResult -- 变化成我们封装好的结果集对象
            searchResult = jestClient.execute(search);
        } catch (IOException e) {
            e.printStackTrace();
        }
        // 结果集转换
        SkuLsResult skuLsResult =  makeResultForSearch(skuLsParams,searchResult);
        // 才是我们想要的数据，Controller中将数据结果集渲染到页面。
        return skuLsResult;
    }

    @Override
    public void incrHotScore(String skuId) {
        // 获取redis
        Jedis jedis = redisUtil.getJedis();
        // 更新规则
        int timesToEs=10;
        // 对redis中商品进行次数累加
        Double hotScore = jedis.zincrby("hotScore", 1, "skuId:" + skuId);

        if (hotScore%timesToEs==0){
            // 更新es
            updateHotScore(skuId,  Math.round(hotScore)  );
        }
    }

    private void updateHotScore(String skuId, long hotScore) {
        // dsl 语句
        String update = "{\n" +
                "   \"doc\":{\n" +
                "     \"hotScore\":"+hotScore+"\n" +
                "   }\n" +
                "}";
        // 更新操作
        Update build = new Update.Builder(update).index(ES_INDEX).type(ES_TYPE).id(skuId).build();

        // 执行操作
        try {
            jestClient.execute(build);
        } catch (IOException e) {
            e.printStackTrace();
        }


    }


    // 执行的结果： searchResult -- 变化成我们封装好的结果集对象
    private SkuLsResult makeResultForSearch(SkuLsParams skuLsParams, SearchResult searchResult) {
        SkuLsResult skuLsResult = new SkuLsResult();
        // 将SkuLsResult 对象的属性分别赋值。赋值：值从哪里来？searchResult
        //        List<SkuLsInfo> skuLsInfoList;
        //        long total;
        //        long totalPages;
        //        List<String> attrValueIdList;
        // 声明一个集合对象来存储查询后的skuLsInfo
        ArrayList<SkuLsInfo> skuLsInfoArrayList = new ArrayList<>();
        // 获取，循环查询出的结果集
        List<SearchResult.Hit<SkuLsInfo, Void>> hits = searchResult.getHits(SkuLsInfo.class);
        for (SearchResult.Hit<SkuLsInfo, Void> hit : hits) {
            // 获取对象
            SkuLsInfo skuLsInfo = hit.source; // 取完数据之后，skuName是高亮的么？
            // 取出高亮字段skuName-将原来的对象值进行覆盖
            if (hit.highlight!=null && hit.highlight.size()>0){
                List<String> list = hit.highlight.get("skuName");
                String skuNameHl  = list.get(0);
                skuLsInfo.setSkuName(skuNameHl);
            }
            // 将skuLsInfo 添加到skuLsInfoArrayList 集合中
            skuLsInfoArrayList.add(skuLsInfo);
        }
        // skuLsInfo 对象 --
        skuLsResult.setSkuLsInfoList(skuLsInfoArrayList);
        // 总数:
        skuLsResult.setTotal(searchResult.getTotal());
        // 总页数：
//      long totalPage = searchResult.getTotal()%skuLsParams.getPageSize()==0?searchResult.getTotal()/skuLsParams.getPageSize():(searchResult.getTotal()/skuLsParams.getPageSize())+1;
        long totalPage = (searchResult.getTotal()+skuLsParams.getPageSize()-1)/skuLsParams.getPageSize();
        skuLsResult.setTotalPages(totalPage);
        // 平台属性值 实际是从聚合中取得
        ArrayList<String> arrayList = new ArrayList<>();
        // 取得数据
        MetricAggregation aggregations = searchResult.getAggregations();
        TermsAggregation groupby_attr = aggregations.getTermsAggregation("groupby_attr");
        List<TermsAggregation.Entry> buckets = groupby_attr.getBuckets();
        for (TermsAggregation.Entry bucket : buckets) {
            String valueId = bucket.getKey();
            arrayList.add(valueId);
        }
        // 平台属性值添加
        skuLsResult.setAttrValueIdList(arrayList);
        return skuLsResult;
    }

    // 编写dsl 语句
    private String makeQueryStringForSearch(SkuLsParams skuLsParams) {
        // query ,bool,filter
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        // skuName 作为关键字
        if (skuLsParams.getKeyword()!=null && skuLsParams.getKeyword().length()>0){
            // match
            MatchQueryBuilder matchQueryBuilder = new MatchQueryBuilder("skuName", skuLsParams.getKeyword());
            boolQueryBuilder.must(matchQueryBuilder);

            // 设置高亮
            HighlightBuilder highlighter = searchSourceBuilder.highlighter();
            // 设置字段
            highlighter.field("skuName");
            highlighter.preTags("<span style='color:red'>");
            highlighter.postTags("</span>");
            // 添加高亮
            searchSourceBuilder.highlight(highlighter);
        }
        // 三级分类Id
        if (skuLsParams.getCatalog3Id()!=null && skuLsParams.getCatalog3Id().length()>0){

            TermQueryBuilder termQueryBuilder = new TermQueryBuilder("catalog3Id",skuLsParams.getCatalog3Id());
            // bool:filter:term
            boolQueryBuilder.filter(termQueryBuilder);
        }
        // 平台属性值
        if (skuLsParams.getValueId()!=null && skuLsParams.getValueId().length>0) {
            for (int i = 0; i < skuLsParams.getValueId().length; i++) {
                // 获取平台属性值
                String valueId = skuLsParams.getValueId()[i];
                TermQueryBuilder termQueryBuilder = new TermQueryBuilder("skuAttrValueList.valueId", valueId);
                boolQueryBuilder.filter(termQueryBuilder);
            }
        }

        // 设置分页
        // 从哪里开始？ pageNo=(pageNo-1)*pageSize
        int form = (skuLsParams.getPageNo()-1)*skuLsParams.getPageSize();
        searchSourceBuilder.from(form);
        searchSourceBuilder.size(skuLsParams.getPageSize());
        // 设置排序 mysql:默认是升序，oracle ：降序
        searchSourceBuilder.sort("hotScore", SortOrder.DESC);

        // 聚合
        // 创建对象 groupby_attr
        // groupby_attr terms:field
        TermsBuilder groupby_attr = AggregationBuilders.terms("groupby_attr").field("skuAttrValueList.valueId");
        searchSourceBuilder.aggregation(groupby_attr);

        // 执行query 方法
        searchSourceBuilder.query(boolQueryBuilder);

        String query = searchSourceBuilder.toString();
        System.out.println("query="+query);
        return query;

    }


}
