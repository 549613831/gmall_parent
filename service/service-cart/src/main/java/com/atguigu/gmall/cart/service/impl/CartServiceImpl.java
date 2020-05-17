package com.atguigu.gmall.cart.service.impl;

import com.atguigu.gmall.cart.mapper.CartInfoMapper;
import com.atguigu.gmall.cart.service.CartService;
import com.atguigu.gmall.common.constant.RedisConst;
import com.atguigu.gmall.model.cart.CartInfo;
import com.atguigu.gmall.model.product.SkuInfo;
import com.atguigu.gmall.product.client.ProductFeignClient;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @Author 伊塔
 * @Description 购物车业务
 * @Date 20:09 2020/5/6
 */
@Service
public class CartServiceImpl implements CartService {
    @Autowired
    private RedisTemplate redisTemplate;
    @Autowired
    private ProductFeignClient productFeignClient;
    @Autowired
    private CartInfoMapper cartInfoMapper;

    @Override
    public void addToCart(Long skuId, String userId, Integer skuNum) {
        // 获取购物车的key
        String cartKey = getCartKey(userId);

        // 添加购物车前需要先判断缓存中是否有key，防止key过期后，再添加数据时，原有数据不存在
        if (!redisTemplate.hasKey(cartKey)) {
            // 如果存在key，则需要从数据库查询，并加入缓存中
            loadCartCache(userId);
        }

        // 查询是否有购物车
        QueryWrapper<CartInfo> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId);
        wrapper.eq("sku_id", skuId);
        CartInfo cartInfoExist = cartInfoMapper.selectOne(wrapper);


        if (cartInfoExist != null) {
            // 订单存在，更新
            cartInfoExist.setSkuPrice(productFeignClient.getSkuPrice(skuId));
            cartInfoExist.setSkuNum(cartInfoExist.getSkuNum() + skuNum);
            cartInfoMapper.updateById(cartInfoExist);
        } else {
            // 订单不存在
            SkuInfo skuInfo = productFeignClient.getSkuInfo(skuId);
            CartInfo cartInfo = new CartInfo();
            cartInfo.setUserId(userId);
            System.out.println(cartInfo.getUserId());
            cartInfo.setSkuId(skuId);
            cartInfo.setCartPrice(skuInfo.getPrice());
            cartInfo.setSkuNum(skuNum);
            cartInfo.setImgUrl(skuInfo.getSkuDefaultImg());
            cartInfo.setSkuName(skuInfo.getSkuName());
            cartInfo.setSkuPrice(skuInfo.getPrice());
            // 添加数据库
            cartInfoMapper.insert(cartInfo);
            cartInfoExist = cartInfo;
        }

        // 更新缓存
        redisTemplate.boundHashOps(cartKey).put(skuId.toString(), cartInfoExist);

        // 设置过期时间
        setCartKeyExpire(cartKey);
    }

    @Override
    public List<CartInfo> getCartList(String userId, String userTempId) {
        List<CartInfo> cartInfoList = new ArrayList<>();

        if (StringUtils.isEmpty(userId)) {
            // 如果用户id为空，则查询数据并返回
            cartInfoList = getCartList(userTempId);
            return cartInfoList;
        }

        if (!StringUtils.isEmpty(userId)) {
            // 用户已登录，此时需要获得临时购物车数据，判断是否需要合并
            List<CartInfo> cartTempList = getCartList(userTempId);
            if (!CollectionUtils.isEmpty(cartTempList)) {
                // 临时购物车有数据，进行合并
                cartInfoList = mergeToCartList(cartTempList, userId);

                // 删除未登录购物车数据
                deleteCartList(userTempId);
            }

            if (StringUtils.isEmpty(userTempId) || CollectionUtils.isEmpty(cartTempList)) {
                // 如果临时id为空，或者临时购物车为空，则直接将登录下购物车数据返回
                // 临时购物车没有数据，不需要合并
                cartInfoList = getCartList(userId);
            }

            return cartInfoList;
        }

        return cartInfoList;
    }

    @Override
    public void checkCart(String userId, Integer isChecked, Long skuId) {
        // 修改数据库数据
        CartInfo cartInfo = new CartInfo();
        cartInfo.setIsChecked(isChecked);
        QueryWrapper<CartInfo> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId).eq("sku_id", skuId);
        cartInfoMapper.update(cartInfo, wrapper);
        
        // 修改缓存
        String cartKey = getCartKey(userId);
        BoundHashOperations<String,String,CartInfo> hashOps = redisTemplate.boundHashOps(cartKey);
        Boolean hasKey = hashOps.hasKey(skuId.toString());
        if (hasKey) {
            // 缓存中存在该数据，获取数据修改再放入缓存
            CartInfo info = hashOps.get(skuId.toString());
            assert info != null;
            info.setIsChecked(isChecked);
            hashOps.put(skuId.toString(), info);
            setCartKeyExpire(cartKey);
        }
    }

    @Override
    public void deleteCart(Long skuId, String userId) {
        // 删除mysql中的数据
        QueryWrapper<CartInfo> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId).eq("sku_id", skuId);
        cartInfoMapper.delete(wrapper);

        // 删除redis中的数据
        String cartKey = getCartKey(userId);
        BoundHashOperations<String,String,CartInfo> hashOps = redisTemplate.boundHashOps(cartKey);
        if (hashOps.hasKey(skuId.toString())) {
            // 存在数据，则删除
            hashOps.delete(skuId.toString());
        }
    }

    @Override
    public List<CartInfo> getCartCheckedList(String userId) {
        List<CartInfo> cartInfoList = new ArrayList<>();
        // 获得保存在redis中的数据的key
        String cartKey = getCartKey(userId);
        List<CartInfo> cartCacheInfoList = redisTemplate.opsForHash().values(cartKey);
        if (!CollectionUtils.isEmpty(cartCacheInfoList)) {
            for (CartInfo cartInfo : cartCacheInfoList) {
                if (cartInfo.getIsChecked() == 1) {
                    // 只有商品在被选中的情况下才返回
                    cartInfoList.add(cartInfo);
                }
            }
        }

        return cartInfoList;
    }

    /**
     * 删除临时购物车数据
     * @param userTempId 临时用户Id
     */
    private void deleteCartList(String userTempId) {
        // 删除数据库中的数据
        QueryWrapper<CartInfo> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userTempId);
        cartInfoMapper.delete(wrapper);

        // 删除缓存中的数据
        String cartKey = getCartKey(userTempId);
        if (redisTemplate.hasKey(cartKey)) {
            // 缓存中存在相关数据
            redisTemplate.delete(cartKey);
        }
    }

    /**
     * 合并购物车数据
     * @param cartTempList 临时购物车数据
     * @param userId 用户id
     * @return 返回合并数据
     */
    private List<CartInfo> mergeToCartList(List<CartInfo> cartTempList, String userId) {
        // 获取已登录购物车数据
        List<CartInfo> cartList = getCartList(userId);
        // 以skuId为key，以cartInfo为value的map集合
        Map<Long, CartInfo> cartInfoMapLogin  = cartList.stream().collect(Collectors.toMap(CartInfo::getSkuId, cartInfo -> cartInfo));

        for (CartInfo cartInfoNoLogin : cartTempList) {
            // 循环未登录购物车集合
            Long skuId = cartInfoNoLogin.getSkuId();
            if (cartInfoMapLogin.containsKey(skuId)) {
                // 如果登录购物车map中包含该key，则合并数量
                CartInfo cartInfo = cartInfoMapLogin.get(skuId);
                cartInfo.setSkuNum(cartInfo.getSkuNum() + cartInfoNoLogin.getSkuNum());

                // 未登录状态选中的商品，在合并后也要设置成选中的
                if (cartInfoNoLogin.getIsChecked() == 1) {
                    cartInfo.setIsChecked(1);
                }

                // 修改数据库
                cartInfoMapper.updateById(cartInfo);
            } else {
                // 不包含这个key，则修改userId后加入数据库
                cartInfoNoLogin.setUserId(userId);
                cartInfoMapper.insert(cartInfoNoLogin);
            }
        }
        // 汇总数据
        return loadCartCache(userId);
    }

    /**
     * 根据用户id查询数据
     * @param userId userId
     * @return 数据
     */
    private List<CartInfo> getCartList(String userId) {
        List<CartInfo> cartInfoList = new ArrayList<>();

        if (StringUtils.isEmpty(userId)) {
            // 如果用户id不存在，则返回一个空对象
            return cartInfoList;
        }

        // 从缓存中查询，首先获得key
        String cartKey = getCartKey(userId);
        cartInfoList = redisTemplate.opsForHash().values(cartKey);
        if (!CollectionUtils.isEmpty(cartInfoList)) {
            // 以更新时间做比较，更新时间最晚的显示在最上面
            cartInfoList.sort((o1, o2) -> o1.getId().compareTo(o2.getId()));
            // 如果数据不为空，则返回
            return cartInfoList;
        } else {
            // 如果缓存中没有数据，则从数据库中查找
            cartInfoList = loadCartCache(userId);
            return cartInfoList;
        }
    }

    @Override
    public List<CartInfo> loadCartCache(String userId) {
        List<CartInfo> cartInfoList;
        // 查询数据库
        QueryWrapper<CartInfo> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId);
        cartInfoList = cartInfoMapper.selectList(wrapper);

        if (CollectionUtils.isEmpty(cartInfoList)) {
            // 如果为空，则数据库中不存在该购物车数据
            return cartInfoList;
        }

        HashMap<String, CartInfo> map = new HashMap<>();
        // 数据库存在购物车数据，将数据放入缓存中
        for (CartInfo cartInfo : cartInfoList) {
            // 缓存失效，有必要更新最新价格
            cartInfo.setSkuPrice(productFeignClient.getSkuPrice(cartInfo.getSkuId()));
            map.put(cartInfo.getSkuId().toString(), cartInfo);
        }

        String cartKey = getCartKey(userId);
        redisTemplate.opsForHash().putAll(cartKey,map);

        // 设置过期时间
        setCartKeyExpire(cartKey);
        return cartInfoList;
    }

    /**
     * 设置过期时间
      * @param cartKey cartKey
     */
    private void setCartKeyExpire(String cartKey) {
        redisTemplate.expire(cartKey, RedisConst.USER_CART_EXPIRE, TimeUnit.SECONDS);
    }


    /**
     * 获取购物车id
     * @param userId 用户id
     * @return 购物车id
     */
    private String getCartKey(String userId) {
        //定义key user:userId:cart
        return RedisConst.USER_KEY_PREFIX + userId + RedisConst.USER_CART_KEY_SUFFIX;
    }
}
