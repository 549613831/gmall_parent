package com.atguigu.gmall.cart.service;

import com.atguigu.gmall.model.cart.CartInfo;

import java.util.List;

public interface CartService {
    /**
     *  添加购物车 用户Id，商品Id，商品数量。
     * @param skuId 商品id
     * @param userId 用户id
     * @param skuNum 商品数量
     */
    void addToCart(Long skuId, String userId, Integer skuNum);

    /**
     * 通过用户Id 查询购物车列表
     * @param userId 用户id
     * @param userTempId 临时id
     * @return 购物车集合
     */
    List<CartInfo> getCartList(String userId, String userTempId);

    /**
     * 更新选中状态
     *
     * @param userId 用户id
     * @param isChecked 当前选中状态
     * @param skuId 商品id
     */
    void checkCart(String userId, Integer isChecked, Long skuId);

    /**
     * 删除购物车数据
     * @param skuId 商品id
     * @param userId 用户id
     */
    void deleteCart(Long skuId, String userId);

    /**
     * 根据用户Id 查询购物车列表
     *
     * @param userId 用户id
     * @return 被选中的购物车列表
     */
    List<CartInfo> getCartCheckedList(String userId);

    /**
     * 通过数据库查询购物车数据并放入缓存
     * @param userId 用户id
     * @return 购物车数据
     */
    List<CartInfo> loadCartCache(String userId);

}
