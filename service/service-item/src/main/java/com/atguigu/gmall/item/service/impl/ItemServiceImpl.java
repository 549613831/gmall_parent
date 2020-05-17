package com.atguigu.gmall.item.service.impl;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.item.service.ItemService;
import com.atguigu.gmall.list.client.ListFeignClient;
import com.atguigu.gmall.model.product.BaseCategoryView;
import com.atguigu.gmall.model.product.SkuInfo;
import com.atguigu.gmall.model.product.SpuSaleAttr;
import com.atguigu.gmall.product.client.ProductFeignClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

@Service
public class ItemServiceImpl implements ItemService {
    @Autowired
    private ProductFeignClient productFeignClient;
    @Autowired
    private ThreadPoolExecutor threadPoolExecutor;
    @Autowired
    private ListFeignClient listFeignClient;

    @Override
    public Map<String, Object> getBySkuId(long skuId) {
        HashMap<String, Object> map = new HashMap<>();

        // 根据skuId获取sku信息
        CompletableFuture<SkuInfo> skuInfoCompletableFuture =
                CompletableFuture.supplyAsync(() -> {
                    SkuInfo skuInfo = productFeignClient.getSkuInfo(skuId);
                    map.put("skuInfo", skuInfo);
                    return skuInfo;
                },threadPoolExecutor);

        // 通过三级分类id查询分类信息
        CompletableFuture<Void> categoryViewCompletableFuture =
                skuInfoCompletableFuture.thenAcceptAsync(skuInfo -> {
                    BaseCategoryView categoryView = productFeignClient.getCategoryView(skuInfo.getCategory3Id());
                    map.put("categoryView", categoryView);
                },threadPoolExecutor);

        // 取sku最新价格
        CompletableFuture<Void> skuPriceCompletableFuture =
                CompletableFuture.runAsync(() -> {
                    BigDecimal price = productFeignClient.getSkuPrice(skuId);
                    map.put("price", price);
                }, threadPoolExecutor);

        // 根据spuId，skuId 查询销售属性集合
        CompletableFuture<Void> spuSaleAttrCompletableFuture =
                skuInfoCompletableFuture.thenAcceptAsync(skuInfo -> {
                    List<SpuSaleAttr> spuSaleAttrListCheckBySku = productFeignClient.getSpuSaleAttrListCheckBySku(skuId, skuInfo.getSpuId());
                    map.put("spuSaleAttrList", spuSaleAttrListCheckBySku);
                },threadPoolExecutor);

        // 据spuId 查询map 集合属性
        CompletableFuture<Void> skuValueIdsMapCompletableFuture =
                skuInfoCompletableFuture.thenAcceptAsync(skuInfo -> {
                    // 据spuId 查询map 集合属性
                    Map skuValueIdsMap = productFeignClient.getSkuValueIdsMap(skuInfo.getSpuId());

                    // 保存json字符串
                    String valuesSkuJson = JSON.toJSONString(skuValueIdsMap);
                    map.put("valuesSkuJson", valuesSkuJson);
                }, threadPoolExecutor);

        // 增加商品热度
        CompletableFuture<Void> incrHotScoreCompletableFuture =
                CompletableFuture.runAsync(() -> listFeignClient.incrHotScore(skuId),threadPoolExecutor);

        CompletableFuture.allOf(
                skuInfoCompletableFuture,
                categoryViewCompletableFuture,
                skuPriceCompletableFuture,
                spuSaleAttrCompletableFuture,
                skuValueIdsMapCompletableFuture,
                incrHotScoreCompletableFuture
        ).join();

        return map;
    }
}
