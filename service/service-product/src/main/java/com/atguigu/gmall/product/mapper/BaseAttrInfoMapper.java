package com.atguigu.gmall.product.mapper;

import com.atguigu.gmall.model.product.BaseAttrInfo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @author 伊塔
 */
@Mapper
public interface BaseAttrInfoMapper extends BaseMapper<BaseAttrInfo> {

    /**
     * 根据分类id，查询平台属性数据
     * @param category1Id 一级类别ID
     * @param category2Id 二级类别ID
     * @param category3Id 三级类别ID
     * @return 台属性数据
     */
    List<BaseAttrInfo> selectBaseAttrInfoList(@Param("category1Id") long category1Id,@Param("category2Id") long category2Id,@Param("category3Id") long category3Id);

    /**
     * 通过skuId 集合来查询数据
     * @param skuId skuId
     * @return 商品对应平台属性
     */
    List<BaseAttrInfo> selectBaseAttrInfoListBySkuId(Long skuId);
}
