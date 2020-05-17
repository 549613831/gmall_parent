package com.atguigu.gmall.product.service;

import com.alibaba.fastjson.JSONObject;
import com.atguigu.gmall.model.product.*;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * @author 伊塔
 */
public interface ManagerService {
    /**
     * 查询所有的一级分类数据
     * @return 一级分类数据集合
     */
    List<BaseCategory1> getCategory1();

    /**
     * 根据一级类别ID查询它的二级类别
     * @param category1Id 一级类别ID
     * @return 二级类别集合
     */
    List<BaseCategory2> getCategory2(long category1Id);

    /**
     * 根据二级类别ID查询它的三级类别
     * @param category2Id 二级类别ID
     * @return 三级类别集合
     */
    List<BaseCategory3> getCategory3(long category2Id);

    /**
     * 根据分类id，查询平台属性数据
     * @param category1Id 一级类别ID
     * @param category2Id 二级类别ID
     * @param category3Id 三级类别ID
     * @return 台属性数据
     */
    List<BaseAttrInfo> getAttrInfoList(long category1Id, long category2Id, long category3Id);

    /**
     * 保存平台属性
     * @param baseAttrInfo 对象
     */
    void saveAttrInfo(BaseAttrInfo baseAttrInfo);

    /**
     * 根据平台属性id查询平台属性，并赋值平台属性值集合
     * @param attrId 平台属性id
     * @return 平台属性
     */
    BaseAttrInfo getAttrInfo(long attrId);

    /**
     * spu分页查询
     * @param pageParam 分页
     * @param spuInfo 前端传递数据
     * @return 分页数据
     */
    IPage<SpuInfo> selectPage(Page<SpuInfo> pageParam, SpuInfo spuInfo);

    /**
     * 查询所有的销售属性集合
     * @return 集合
     */
    List<BaseSaleAttr> getBaseSaleAttrList();

    /**
     * 保存spu信息
     * @param spuInfo spu对象
     */
    void saveSpuInfo(SpuInfo spuInfo);

    /**
     * 获取相关图片集合
     * @param spuId spu id
     * @return 集合
     */
    List<SpuImage> getSpuImageList(Long spuId);

    /**
     * 获取销售属性
     * @param spuId spu id
     * @return 销售属性集合
     */
    List<SpuSaleAttr> getSpuSaleAttrList(Long spuId);

    /**
     * 保存sku信息
     * @param skuInfo 前端上传数据
     */
    void saveSkuInfo(SkuInfo skuInfo);

    /**
     * 查询sku分页数据
     * @param skuInfoPage 分页条件
     * @return 分页数据
     */
    IPage<SkuInfo> selectPage(Page<SkuInfo> skuInfoPage);

    /**
     * 商品上架
     * @param skuId skuId
     */
    void onSale(Long skuId);

    /**
     * 商品下架
     * @param skuId skuId
     */
    void cancelSale(Long skuId);

    /**
     * 根据skuId 查询skuInfo
     * @param skuId skuId
     * @return skuInfo
     */
    SkuInfo getSkuInfo(Long skuId);

    /**
     * 通过三级分类id查询分类信息
     * @param category3Id category3Id
     * @return 分类信息
     */
    BaseCategoryView getCategoryViewByCategory3Id(Long category3Id);

    /**
     * 获取sku价格
     * @param skuId skuId
     * @return 价格
     */
    BigDecimal getSkuPrice(Long skuId);

    /**
     * 根据spuId，skuId 查询销售属性集合
     * @param skuId skuId
     * @param spuId spuId
     * @return 销售属性集合
     */
    List<SpuSaleAttr> getSpuSaleAttrListCheckBySku(Long skuId, Long spuId);


    /**
     * 根据spuId 查询map 集合属性
     * @param spuId spuId
     * @return 集合属性
     */
    Map getSkuValueIdsMap(Long spuId);

    /**
     * 获取全部分类信息
     * @return 全部分类信息
     */
    List<JSONObject> getBaseCategoryList();

    /**
     * 通过品牌Id 来查询数据
     * @param tmId 品牌id
     * @return 品牌数据
     */
    BaseTrademark getTrademarkByTmId(Long tmId);

    /**
     * 通过skuId 集合来查询数据
     * @param skuId skuId
     * @return 商品对应平台属性
     */
    List<BaseAttrInfo> getAttrList(Long skuId);

}
