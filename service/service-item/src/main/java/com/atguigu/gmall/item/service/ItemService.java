package com.atguigu.gmall.item.service;


import java.util.Map;

public interface ItemService {
    /**
     * 根据skuId获取item信息
     * @param skuId skuId
     * @return map格式数据
     */
    Map<String, Object> getBySkuId(long skuId);
}
