package com.atguigu.gmall.list.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.atguigu.gmall.list.repository.GoodsRepository;
import com.atguigu.gmall.list.service.SearchService;
import com.atguigu.gmall.model.list.*;
import com.atguigu.gmall.model.product.*;
import com.atguigu.gmall.product.client.ProductFeignClient;
import org.apache.commons.lang.StringUtils;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.text.Text;
import org.elasticsearch.index.query.*;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.nested.NestedAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.nested.ParsedNested;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedLongTerms;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedStringTerms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.swing.*;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SearchServiceImpl implements SearchService {
    @Autowired
    private GoodsRepository goodsRepository;
    @Autowired
    private ProductFeignClient productFeignClient;
    @Autowired
    private RedisTemplate redisTemplate;
    @Autowired
    private RestHighLevelClient restHighLevelClient;


    @Override
    public void upperGoods(Long skuId) {
        Goods good = new Goods();

        // 查询商品信息
        SkuInfo skuInfo = productFeignClient.getSkuInfo(skuId);
        if (skuInfo != null) {
            good.setId(skuInfo.getId());
            good.setDefaultImg(skuInfo.getSkuDefaultImg());
            good.setTitle(skuInfo.getSkuName());
            good.setPrice(skuInfo.getPrice().doubleValue());
            good.setCreateTime(new Date());

            // 获取品牌信息
            BaseTrademark trademark = productFeignClient.getTrademark(skuInfo.getTmId());
            if (trademark != null) {
                good.setTmId(trademark.getId());
                good.setTmName(trademark.getTmName());
                good.setTmLogoUrl(trademark.getLogoUrl());
            }

            // 获取分类数据信息
            BaseCategoryView categoryView = productFeignClient.getCategoryView(skuInfo.getCategory3Id());
            if (categoryView != null) {
                good.setCategory1Id(categoryView.getCategory1Id());
                good.setCategory1Name(categoryView.getCategory1Name());
                good.setCategory2Id(categoryView.getCategory2Id());
                good.setCategory2Name(categoryView.getCategory2Name());
                good.setCategory3Id(categoryView.getCategory3Id());
                good.setCategory3Name(categoryView.getCategory3Name());
            }
        }

        // 获取平台属性集合对象
        List<BaseAttrInfo> attrList = productFeignClient.getAttrList(skuId);
        if (attrList != null && !attrList.isEmpty()) {
            List<SearchAttr> collect = attrList.stream().map(baseAttrInfo -> {
                SearchAttr searchAttr = new SearchAttr();
                searchAttr.setAttrId(baseAttrInfo.getId());
                searchAttr.setAttrName(baseAttrInfo.getAttrName());
                String valueName = baseAttrInfo.getAttrValueList().get(0).getValueName();
                searchAttr.setAttrValue(valueName);

                return searchAttr;
            }).collect(Collectors.toList());

            good.setAttrs(collect);
        }

        // 保存
        goodsRepository.save(good);
    }

    @Override
    public void lowerGoods(Long skuId) {
        goodsRepository.deleteById(skuId);
    }

    @Override
    public void incrHotScore(Long skuId) {
        String hotKey = "hotScore";
        Double score = redisTemplate.opsForZSet().incrementScore(hotKey, "skuId:" + skuId, 1);
        if (score % 10 == 0) {
            // 更新es
            Optional<Goods> optional = goodsRepository.findById(skuId);
            Goods good = optional.get();
            good.setHotScore(Math.round(score));
            goodsRepository.save(good);
        }
    }

    @Override
    public SearchResponseVo search(SearchParam searchParam) throws IOException {
        // 构建dsl语句
        SearchRequest searchRequest = buildQueryDsl(searchParam);

        // 执行dsl语句
        SearchResponse response = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);

        // 获取结果并封装
        SearchResponseVo responseVo = parseSearchResult(response);
        responseVo.setPageSize(searchParam.getPageSize());
        responseVo.setPageNo(searchParam.getPageNo());
        long totalPages = (responseVo.getTotal() + searchParam.getPageSize() - 1) / searchParam.getPageSize();
        responseVo.setTotalPages(totalPages);


        return responseVo;
    }

    /**
     * 获取结果集封装成对象
     *
     * @param response 结果集
     * @return 对象
     */
    private SearchResponseVo parseSearchResult(SearchResponse response) {
        SearchResponseVo searchResponseVo = new SearchResponseVo();
        // 品牌数据通过聚合得到的！
        Map<String, Aggregation> aggregationMap = response.getAggregations().asMap();
        // 获取品牌Id Aggregation接口中并没有获取到桶的方法，所以在这进行转化,ParsedLongTerms 是他的实现
        ParsedLongTerms tmIdAgg = (ParsedLongTerms) aggregationMap.get("tmIdAgg");
        // 从桶中获取数据
        List<SearchResponseTmVo> trademarkList = tmIdAgg.getBuckets().stream().map(bucket -> {
            SearchResponseTmVo searchResponseTmVo = new SearchResponseTmVo();
            // 获取品牌id
            searchResponseTmVo.setTmId(Long.parseLong(bucket.getKeyAsString()));

            // 获取品牌名称
            Map<String, Aggregation> tmIdSubAggregationMap = bucket.getAggregations().asMap();
            // tmNameAgg 品牌名称的agg 品牌数据类型是String
            ParsedStringTerms tmNameAgg = (ParsedStringTerms) tmIdSubAggregationMap.get("tmNameAgg");
            // 获取品牌名称并赋值
            String tmName = tmNameAgg.getBuckets().get(0).getKeyAsString();
            searchResponseTmVo.setTmName(tmName);

            // 获取品牌的logo
            ParsedStringTerms tmLogoUrlAgg = (ParsedStringTerms) tmIdSubAggregationMap.get("tmLogoUrlAgg");
            String tmLogoUrl = tmLogoUrlAgg.getBuckets().get(0).getKeyAsString();
            searchResponseTmVo.setTmLogoUrl(tmLogoUrl);

            // 返回品牌
            return searchResponseTmVo;
        }).collect(Collectors.toList());

        // 赋值品牌属性
        searchResponseVo.setTrademarkList(trademarkList);

        // 获取平台属性数据 应该也是从聚合中获取 attrAgg 数据类型是nested ，转化一下
        ParsedNested tmNameAgg = (ParsedNested) aggregationMap.get("attrAgg");
        // 获取attrIdAgg 平台属性Id 数据
        ParsedLongTerms attrIdAgg = tmNameAgg.getAggregations().get("attrIdAgg");
        List<? extends Terms.Bucket> buckets = attrIdAgg.getBuckets();
        // 判断桶的集合不为空
        if (buckets != null && !buckets.isEmpty()) {
            // 循环遍历数据
            List<SearchResponseAttrVo> attrsList = buckets.stream().map(bucket -> {
                SearchResponseAttrVo searchResponseAttrVo = new SearchResponseAttrVo();
                // 设置平台属性id
                searchResponseAttrVo.setAttrId(bucket.getKeyAsNumber().longValue());
                // 获取attrNameAgg 中的数据 名称数据类型是String
                ParsedStringTerms attrNameAgg = bucket.getAggregations().get("attrNameAgg");
                searchResponseAttrVo.setAttrName(attrNameAgg.getBuckets().get(0).getKeyAsString());

                // 赋值平台属性值集合 获取attrValueAgg
                ParsedStringTerms attrValueAgg = bucket.getAggregations().get("attrValueAgg");
                List<? extends Terms.Bucket> valueBuckets = attrValueAgg.getBuckets();
                // 获取该valueBuckets 中的数据
                // 将集合转化为map ，map的key 就是桶key，通过key获取里面的数据，并将数据变成一个list集合
                List<String> valueList = valueBuckets.stream().map(Terms.Bucket::getKeyAsString).collect(Collectors.toList());
                searchResponseAttrVo.setAttrValueList(valueList);
                return searchResponseAttrVo;
            }).collect(Collectors.toList());

            searchResponseVo.setAttrsList(attrsList);
        }

        // 获取商品数据 goodsList 声明一个存储商品的集合
        ArrayList<Goods> goodsArrayList = new ArrayList<>();
        // 品牌数据需要从查询结果集中获取。
        SearchHits hits = response.getHits();
        SearchHit[] subHits = hits.getHits();
        if (subHits != null && subHits.length > 0) {
            // 循环遍历数据
            for (SearchHit subHit : subHits) {
                // 获取商品的json串
                String sourceAsString = subHit.getSourceAsString();
                // 将json串编程Goods.class
                Goods goods = JSONObject.parseObject(sourceAsString, Goods.class);
                // 获取商品的时候，如果按照商品名称查询时，商品的名称显示的时候，应该高亮。但是，现在这个名称不是高亮 从高亮中获取商品名称
                if (subHit.getHighlightFields().get("title") != null) {
                    // 说明当前用户查询是按照全文检索的方式查询的。
                    Text title = subHit.getHighlightFields().get("title").getFragments()[0];
                    goods.setTitle(title.toString());
                }
                // 添加商品到集合
                goodsArrayList.add(goods);
            }
        }
        searchResponseVo.setGoodsList(goodsArrayList);
        // 总记录数
        searchResponseVo.setTotal(hits.totalHits);


        return searchResponseVo;
    }

    /**
     * 自动生成es查询语句
     *
     * @param searchParam vo数据
     * @return es查询语句
     */
    private SearchRequest buildQueryDsl(SearchParam searchParam) {
        if (searchParam == null) {
            return null;
        }

        // 构建查询器
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        // 构建boolQueryBuilder
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();

        // 判断查询条件是否为空
        if (StringUtils.isNotEmpty(searchParam.getKeyword())) {
            MatchQueryBuilder title = new MatchQueryBuilder("title", searchParam.getKeyword()).operator(Operator.OR);
            boolQueryBuilder.must(title);
        }

        // 构建品牌查询 trademark=2:华为
        String trademark = searchParam.getTrademark();
        if (StringUtils.isNotEmpty(trademark)) {
            String[] split = StringUtils.split(trademark, ":");

            if (split != null && split.length == 2) {
                // 根据品牌id过滤
                TermQueryBuilder tmId = QueryBuilders.termQuery("tmId", split[0]);
                boolQueryBuilder.filter(tmId);
            }
        }

        // 构建分类过滤
        if (searchParam.getCategory1Id() != null) {
            boolQueryBuilder.filter(QueryBuilders.termQuery("category1Id", searchParam.getCategory1Id()));
        }
        if (searchParam.getCategory2Id() != null) {
            boolQueryBuilder.filter(QueryBuilders.termQuery("category2Id", searchParam.getCategory2Id()));
        }
        if (searchParam.getCategory3Id() != null) {
            boolQueryBuilder.filter(QueryBuilders.termQuery("category3Id", searchParam.getCategory3Id()));
        }

        // 构建平台属性查询 23:4G:运行内存
        String[] props = searchParam.getProps();
        if (props != null && props.length > 0) {
            for (String prop : props) {
                String[] split = StringUtils.split(prop, ":");
                if (split != null && split.length == 3) {
                    // 构建查询
                    BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
                    BoolQueryBuilder subBoolQuery = QueryBuilders.boolQuery();
                    // 构建查询中的过滤条件
                    subBoolQuery.must(QueryBuilders.termQuery("attrs.attrId", split[0]));
                    subBoolQuery.must(QueryBuilders.termQuery("attrs.attrValue", split[1]));
                    // 将boolQuery放入subBoolQuery
                    boolQuery.must(QueryBuilders.nestedQuery("attrs", subBoolQuery, ScoreMode.None));
                    // 放入总查询器
                    boolQueryBuilder.filter(boolQuery);
                }
            }
        }

        // 放入查询器的query字段
        searchSourceBuilder.query(boolQueryBuilder);

        // 构建分页
        int from = (searchParam.getPageNo() - 1) * searchParam.getPageSize();
        searchSourceBuilder.from(from);
        searchSourceBuilder.size(searchParam.getPageSize());

        // 排序 1:hotScore
        String order = searchParam.getOrder();
        if (StringUtils.isNotEmpty(order)) {
            String[] split = StringUtils.split(order, ":");
            if (split != null && split.length == 2) {
                // 设置排序字段
                String field = null;
                switch (split[0]) {
                    case "1":
                        field = "hotScore";
                        break;
                    case "2":
                        field = "price";
                        break;
                    default:
                        break;
                }

                // 设置排序规则
                searchSourceBuilder.sort(field, "asc".equals(split[1]) ? SortOrder.ASC : SortOrder.DESC);
            } else {
                // 默认根据热度排名降序排列
                searchSourceBuilder.sort("hotScore", SortOrder.DESC);
            }
        }

        // 高亮 ,声明高亮对象，设置高亮规则
        HighlightBuilder highlightBuilder = new HighlightBuilder();
        highlightBuilder.field("title");
        highlightBuilder.postTags("</span>");
        highlightBuilder.preTags("<span style=color:red>");
        searchSourceBuilder.highlighter(highlightBuilder);

        // 聚合
        TermsAggregationBuilder termsAggregationBuilder = AggregationBuilders.terms("tmIdAgg").field("tmId")
                .subAggregation(AggregationBuilders.terms("tmNameAgg").field("tmName"))
                .subAggregation(AggregationBuilders.terms("tmLogoUrlAgg").field("tmLogoUrl"));

        // 将聚合规则添加到查询器 也就是aggs字段下
        searchSourceBuilder.aggregation(termsAggregationBuilder);

        // 平台属性，设置nested聚合
        NestedAggregationBuilder nestedAggregationBuilder = AggregationBuilders.nested("attrAgg", "attrs")
                .subAggregation(AggregationBuilders.terms("attrIdAgg").field("attrs.attrId")
                        .subAggregation(AggregationBuilders.terms("attrNameAgg").field("attrs.attrName"))
                        .subAggregation(AggregationBuilders.terms("attrValueAgg").field("attrs.attrValue")));
        searchSourceBuilder.aggregation(nestedAggregationBuilder);

        // 设置有效数据，查询的时候哪些字段显示
        searchSourceBuilder.fetchSource(new String[]{"id", "defaultImg", "title", "price"}, null);

        // 设置index type
        SearchRequest request = new SearchRequest("goods");
        request.types("info");
        request.source(searchSourceBuilder);

        // 打印dsl语句
        String s = searchSourceBuilder.toString();
        System.out.println("dsl = " + s);
        return request;
    }
}
