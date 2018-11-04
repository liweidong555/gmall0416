package com.atguigu.gmall0416.list;

import io.searchbox.client.JestClient;
import io.searchbox.core.Search;
import io.searchbox.core.SearchResult;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;

@RunWith(SpringRunner.class)
@SpringBootTest
public class GmallListServiceApplicationTests {

	@Autowired
	private JestClient jestClient;

	@Test
	public void contextLoads() {
	}

	@Test
	public void testE() throws IOException {

		// 准备dsl语句
		String strQuery = "{\n" +
				"  \"query\": {\n" +
				"    \"match\": {\n" +
				"      \"actorList.name\": \"张译\"\n" +
				"    }\n" +
				"  }\n" +
				"}";

		// dsl 语句在哪里执行？ index-type-search
		Search search = new Search.Builder(strQuery).addIndex("movie_chn").addType("movie_type_chn").build();
		SearchResult result = jestClient.execute(search);
		// 取得执行结果
		//		"hits": {
//				"total": 1,
//				"max_score": 0.2876821,
//				"hits": [
//				{
//					"_index": "movie_chn",
//					"_type": "movie_type_chn",
//					"_id": "1",
//					"_score": 0.2876821,
//					"_source": {
//					    "id": 1,
//						"name": "红海行动",
//						"doubanScore": 8.5,
//						"actorList": [
//						{
//							"id": 1,
//							"name": "张译"
//						},
//						{
//							"id": 2,
//							"name": "海清"
//						},
//						{
//							"id": 3,
//							"name": "张涵予"
//						}
//         			 ]
//					}

		List<SearchResult.Hit<HashMap, Void>> hits = result.getHits(HashMap.class);
		for (SearchResult.Hit<HashMap, Void> hit : hits) {
			HashMap map = hit.source;
			String name = (String) map.get("name");
			Object actorList = map.get("actorList");
			System.out.println(name+"===========");
			System.out.println(map+"===========");
			System.out.println(actorList+"==========="+"王晨gay");
		}


	}

}
