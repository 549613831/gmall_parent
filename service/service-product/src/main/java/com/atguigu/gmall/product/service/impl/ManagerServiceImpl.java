package com.atguigu.gmall.product.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.atguigu.gmall.common.cache.GmallCache;
import com.atguigu.gmall.common.constant.MqConst;
import com.atguigu.gmall.common.constant.RedisConst;
import com.atguigu.gmall.common.service.RabbitService;
import com.atguigu.gmall.model.product.*;
import com.atguigu.gmall.product.mapper.*;
import com.atguigu.gmall.product.service.ManagerService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author 伊塔
 */
@Service
public class ManagerServiceImpl implements ManagerService {
    @Autowired
    private BaseCategory1Mapper baseCategory1Mapper;

    @Autowired
    private BaseCategory2Mapper baseCategory2Mapper;

    @Autowired
    private BaseCategory3Mapper baseCategory3Mapper;

    @Autowired
    private BaseAttrInfoMapper baseAttrInfoMapper;

    @Autowired
    private BaseAttrValueMapper baseAttrValueMapper;

    @Autowired
    private SpuInfoMapper spuInfoMapper;

    @Autowired
    private BaseSaleAttrMapper baseSaleAttrMapper;

    @Autowired
    private SpuSaleAttrMapper spuSaleAttrMapper;

    @Autowired
    private SpuSaleAttrValueMapper spuSaleAttrValueMapper;

    @Autowired
    private SpuImageMapper spuImageMapper;

    @Autowired
    private SkuInfoMapper skuInfoMapper;

    @Autowired
    private SkuImageMapper skuImageMapper;

    @Autowired
    private SkuAttrValueMapper skuAttrValueMapper;

    @Autowired
    private SkuSaleAttrValueMapper skuSaleAttrValueMapper;

    @Autowired
    private BaseCategoryViewMapper baseCategoryViewMapper;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private BaseTrademarkMapper baseTrademarkMapper;

    @Autowired
    private RabbitService rabbitService;

    @Override
    public List<BaseCategory1> getCategory1() {
        return baseCategory1Mapper.selectList(null);
    }

    @Override
    public List<BaseCategory2> getCategory2(long category1Id) {
        return baseCategory2Mapper.selectList(
                new QueryWrapper<BaseCategory2>().eq("category1_id", category1Id)
        );
    }

    @Override
    public List<BaseCategory3> getCategory3(long category2Id) {
        return baseCategory3Mapper.selectList(
                new QueryWrapper<BaseCategory3>().eq("category2_id", category2Id)
        );
    }

    @Override
    public List<BaseAttrInfo> getAttrInfoList(long category1Id, long category2Id, long category3Id) {
        return baseAttrInfoMapper.selectBaseAttrInfoList(category1Id, category2Id, category3Id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveAttrInfo(BaseAttrInfo baseAttrInfo) {
        // 判断时新增，还是修改
        if (baseAttrInfo.getId() == null) {
            // 是新增平台属性
            baseAttrInfoMapper.insert(baseAttrInfo);
        } else {
            // 修改
            baseAttrInfoMapper.updateById(baseAttrInfo);
        }

        // 无法获得具体的平台属性值，先将所有平台属性值删除，再添加
        QueryWrapper<BaseAttrValue> wrapper = new QueryWrapper<>();
        wrapper.eq("attr_id", baseAttrInfo.getId());
        baseAttrValueMapper.delete(wrapper);

        List<BaseAttrValue> attrValueList = baseAttrInfo.getAttrValueList();
        for (BaseAttrValue baseAttrValue : attrValueList) {
            baseAttrValue.setAttrId(baseAttrInfo.getId());
            baseAttrValueMapper.insert(baseAttrValue);
        }
    }

    @Override
    public BaseAttrInfo getAttrInfo(long attrId) {
        BaseAttrInfo baseAttrInfo = baseAttrInfoMapper.selectById(attrId);
        // 根据平台属性id获取平台属性值
        baseAttrInfo.setAttrValueList(getAttrValueList(attrId));
        return baseAttrInfo;
    }

    @Override
    public IPage<SpuInfo> selectPage(Page<SpuInfo> pageParam, SpuInfo spuInfo) {
        QueryWrapper<SpuInfo> wrapper = new QueryWrapper<>();
        wrapper.eq("category3_id", spuInfo.getCategory3Id());
        wrapper.orderByDesc("id");
        return spuInfoMapper.selectPage(pageParam, wrapper);
    }

    @Override
    public List<BaseSaleAttr> getBaseSaleAttrList() {
        return baseSaleAttrMapper.selectList(null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveSpuInfo(SpuInfo spuInfo) {
        // 商品表保存信息
        spuInfoMapper.insert(spuInfo);

        // 保存销售属性信息
        List<SpuSaleAttr> spuSaleAttrList = spuInfo.getSpuSaleAttrList();
        if (spuSaleAttrList != null && !spuSaleAttrList.isEmpty()) {
            for (SpuSaleAttr spuSaleAttr : spuSaleAttrList) {
                spuSaleAttr.setSpuId(spuInfo.getId());
                spuSaleAttrMapper.insert(spuSaleAttr);

                // 保存销售属性值信息
                List<SpuSaleAttrValue> spuSaleAttrValueList = spuSaleAttr.getSpuSaleAttrValueList();
                if (spuSaleAttrValueList != null && !spuSaleAttrValueList.isEmpty()) {
                    for (SpuSaleAttrValue spuSaleAttrValue : spuSaleAttrValueList) {
                        spuSaleAttrValue.setSpuId(spuInfo.getId());
                        spuSaleAttrValue.setSaleAttrName(spuSaleAttr.getSaleAttrName());
                        spuSaleAttrValueMapper.insert(spuSaleAttrValue);
                    }
                }
            }
        }

        // 保存图片信息
        List<SpuImage> spuImageList = spuInfo.getSpuImageList();
        if (spuImageList != null && !spuImageList.isEmpty()) {
            for (SpuImage spuImage : spuImageList) {
                spuImage.setSpuId(spuInfo.getId());
                spuImageMapper.insert(spuImage);
            }
        }


    }

    @Override
    public List<SpuImage> getSpuImageList(Long spuId) {
        QueryWrapper<SpuImage> wrapper = new QueryWrapper<>();
        wrapper.eq("spu_id", spuId);
        return spuImageMapper.selectList(wrapper);
    }

    @Override
    public List<SpuSaleAttr> getSpuSaleAttrList(Long spuId) {
        return spuSaleAttrMapper.selectSpuSaleAttrList(spuId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveSkuInfo(SkuInfo skuInfo) {
        if (skuInfo == null) {
            return;
        }
        // 保存skuInfo数据
        skuInfoMapper.insert(skuInfo);


        // 保存图片数据
        List<SkuImage> skuImageList = skuInfo.getSkuImageList();
        if (skuImageList != null && !skuImageList.isEmpty()) {
            for (SkuImage skuImage : skuImageList) {
                // 设置spuId
                skuImage.setSkuId(skuInfo.getId());
                skuImageMapper.insert(skuImage);
            }
        }

        // 保存平台属性
        List<SkuAttrValue> skuAttrValueList = skuInfo.getSkuAttrValueList();
        if (skuAttrValueList != null && !skuAttrValueList.isEmpty()) {
            for (SkuAttrValue skuAttrValue : skuAttrValueList) {
                skuAttrValue.setSkuId(skuInfo.getId());
                skuAttrValueMapper.insert(skuAttrValue);
            }
        }

        // 保存销售属性
        List<SkuSaleAttrValue> skuSaleAttrValueList = skuInfo.getSkuSaleAttrValueList();
        if (skuSaleAttrValueList != null && !skuSaleAttrValueList.isEmpty()) {
            for (SkuSaleAttrValue skuSaleAttrValue : skuSaleAttrValueList) {
                skuSaleAttrValue.setSpuId(skuInfo.getSpuId());
                skuSaleAttrValue.setSkuId(skuInfo.getId());
                skuSaleAttrValueMapper.insert(skuSaleAttrValue);
            }
        }

        // 通知es上架商品
        rabbitService.sendMessage(
                MqConst.EXCHANGE_DIRECT_GOODS,
                MqConst.ROUTING_GOODS_UPPER,
                skuInfo.getId());
    }

    @Override
    public IPage<SkuInfo> selectPage(Page<SkuInfo> skuInfoPage) {
        QueryWrapper<SkuInfo> wrapper = new QueryWrapper<>();
        wrapper.orderByDesc("id");
        return skuInfoMapper.selectPage(skuInfoPage, wrapper);
    }

    @Override
    public void onSale(Long skuId) {
        SkuInfo skuInfo = new SkuInfo();
        skuInfo.setIsSale(1);
        skuInfo.setId(skuId);
        skuInfoMapper.updateById(skuInfo);

        // 通知es
        rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_GOODS, MqConst.ROUTING_GOODS_UPPER, skuId);
    }

    @Override
    public void cancelSale(Long skuId) {
        SkuInfo skuInfo = new SkuInfo();
        skuInfo.setIsSale(0);
        skuInfo.setId(skuId);
        skuInfoMapper.updateById(skuInfo);

        //商品下架
        rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_GOODS, MqConst.ROUTING_GOODS_LOWER, skuId);

    }

    @Override
    @GmallCache(prefix = RedisConst.SKUKEY_PREFIX)
    public SkuInfo getSkuInfo(Long skuId) {
        return getSkuInfoDb(skuId);
    }

    private SkuInfo getSkuInfoRedisson(Long skuId) {
        SkuInfo skuInfo = null;
        try {
            // 获取对应skuId的key
            String skuKey = RedisConst.SKUKEY_PREFIX + skuId + RedisConst.SKUKEY_SUFFIX;

            // 从redis中获取
            skuInfo = (SkuInfo) redisTemplate.opsForValue().get(skuKey);

            if (skuInfo == null) {
                // 缓存中不存在，使用分布式锁
                String lockKey = RedisConst.SKUKEY_PREFIX + skuId + RedisConst.SKULOCK_SUFFIX;

                RLock lock = redissonClient.getLock(lockKey);
                // 尝试获得锁
                boolean res = lock.tryLock(RedisConst.SKULOCK_EXPIRE_PX1, RedisConst.SKULOCK_EXPIRE_PX2, TimeUnit.SECONDS);

                if (res) {
                    try {
                        // 如果能够获得锁，则先从数据库中获取skuInfo
                        skuInfo = getSkuInfoDb(skuId);

                        if (skuInfo == null) {
                            // 如果从数据库中获得不到，为了避免缓存穿透，创建一个对象并放入缓存中
                            SkuInfo skuInfo1 = new SkuInfo();

                            redisTemplate.opsForValue().set(skuKey, skuInfo1, RedisConst.SKUKEY_TIMEOUT, TimeUnit.SECONDS);

                            return skuInfo1;
                        }

                        // 从数据库中查询出来的数据要是不为空，则放入缓存中，并返回
                        redisTemplate.opsForValue().set(skuKey, skuInfo, RedisConst.SKUKEY_TIMEOUT, TimeUnit.SECONDS);

                        return skuInfo;
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        lock.unlock();
                    }
                } else {
                    // 其他线程等待
                    Thread.sleep(1000);
                    return getSkuInfo(skuId);
                }
            } else {
                // 有可能获取的对象是为了防止缓存穿透而存入的对象，如果是这样的就不需要返回
                if (skuInfo.getId() == null) {
                    return null;
                }
                // 缓存中存在，直接返回
                return skuInfo;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 防止缓存宕机，从数据库中查询
        return getSkuInfoDb(skuId);
    }

    /**
     * 通过redis获得锁查询缓存
     *
     * @param skuId skuId
     * @return skuInfo
     */
    private SkuInfo getSkuInfoRedis(Long skuId) {
        SkuInfo skuInfo = null;
        try {
            // 获取对应skuId的key
            String skuKey = RedisConst.SKUKEY_PREFIX + skuId + RedisConst.SKUKEY_SUFFIX;

            // 从redis中获取
            skuInfo = (SkuInfo) redisTemplate.opsForValue().get(skuKey);

            if (skuInfo == null) {
                // 缓存中不存在，使用分布式锁
                String lockKey = RedisConst.SKUKEY_PREFIX + skuId + RedisConst.SKULOCK_SUFFIX;
                String uuid = UUID.randomUUID().toString();

                Boolean ifExist = redisTemplate.opsForValue().setIfAbsent(lockKey, uuid, RedisConst.SKULOCK_EXPIRE_PX2, TimeUnit.SECONDS);

                if (ifExist) {
                    // 如果能够获得锁，则先从数据库中获取skuInfo
                    skuInfo = getSkuInfoDb(skuId);

                    if (skuInfo == null) {
                        // 如果从数据库中获得不到，为了避免缓存穿透，创建一个对象并放入缓存中
                        SkuInfo skuInfo1 = new SkuInfo();

                        redisTemplate.opsForValue().set(skuKey, skuInfo1, RedisConst.SKUKEY_TIMEOUT, TimeUnit.SECONDS);

                        return skuInfo1;
                    }

                    // 从数据库中查询出来的数据要是不为空，则放入缓存中，并返回
                    redisTemplate.opsForValue().set(skuKey, skuInfo, RedisConst.SKUKEY_TIMEOUT, TimeUnit.SECONDS);

                    // 解锁
                    String script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
                    DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
                    redisScript.setResultType(Long.class);
                    redisScript.setScriptText(script);
                    redisTemplate.execute(redisScript, Collections.singletonList(lockKey), uuid);

                    return skuInfo;
                } else {
                    // 没有获取到锁，等到一秒后再次查询
                    Thread.sleep(1000);
                    return getSkuInfo(skuId);
                }

            } else {
                // 有可能获取的对象是为了防止缓存穿透而存入的对象，如果是这样的就不需要返回
                if (skuInfo.getId() == null) {
                    return null;
                }
                // 缓存中存在，直接返回
                return skuInfo;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 防止缓存宕机，从数据库中查询
        return getSkuInfoDb(skuId);
    }

    /**
     * 通过查询数据库获取skuInfo
     *
     * @param skuId skuId
     * @return skuInfo
     */
    private SkuInfo getSkuInfoDb(Long skuId) {
        // 获取sku信息
        SkuInfo skuInfo = skuInfoMapper.selectById(skuId);

        // 获取图片信息
        if (skuInfo != null) {
            QueryWrapper<SkuImage> wrapper = new QueryWrapper<>();
            wrapper.eq("sku_id", skuId);
            List<SkuImage> imageList = skuImageMapper.selectList(wrapper);
            skuInfo.setSkuImageList(imageList);
        }

        return skuInfo;
    }

    @Override
    @GmallCache(prefix = "categoryViewByCategory3Id:")
    public BaseCategoryView getCategoryViewByCategory3Id(Long category3Id) {
        return baseCategoryViewMapper.selectById(category3Id);
    }

    @Override
    @GmallCache(prefix = "skuPrice:")
    public BigDecimal getSkuPrice(Long skuId) {
        SkuInfo skuInfo = skuInfoMapper.selectById(skuId);
        if (skuInfo != null) {
            return skuInfo.getPrice();
        }
        return new BigDecimal("0");
    }

    @Override
    @GmallCache(prefix = "spuSaleAttrListCheckBySku:")
    public List<SpuSaleAttr> getSpuSaleAttrListCheckBySku(Long skuId, Long spuId) {
        return spuSaleAttrMapper.selectSpuSaleAttrListCheckBySku(skuId, spuId);
    }

    @Override
    @GmallCache(prefix = "skuValueIdsMap:")
    public Map getSkuValueIdsMap(Long spuId) {
        Map<Object, Object> map = new HashMap<>();
        // key = 125|123 ,value = 37
        List<Map> mapList = skuSaleAttrValueMapper.getSaleAttrValuesBySpu(spuId);
        if (mapList != null && !mapList.isEmpty()) {
            // 循环遍历
            for (Map skuMap : mapList) {
                // key = 125|123 ,value = 37
                map.put(skuMap.get("value_ids"), skuMap.get("sku_id"));
            }
        }
        return map;

    }

    @Override
    @GmallCache(prefix = "baseCategoryList")
    public List<JSONObject> getBaseCategoryList() {
        List<JSONObject> list = new ArrayList<>();
        List<BaseCategoryView> categoryViews = baseCategoryViewMapper.selectList(null);

        // 根据category1Id进行分类
        Map<Long, List<BaseCategoryView>> category1Map = categoryViews.stream()
                .collect(Collectors.groupingBy(BaseCategoryView::getCategory1Id));

        int index = 1;
        
        // 获取一级分类下所有数据
        for (Map.Entry<Long, List<BaseCategoryView>> entry1 : category1Map.entrySet()) {
            // 获取一级分类id
            Long category1Id = entry1.getKey();
            
            //  获取一级分类下面的所有集合
            List<BaseCategoryView> category2List1 = entry1.getValue();

            JSONObject category1 = new JSONObject();
            category1.put("index", index++);
            category1.put("categoryId", category1Id);

            // 获取一级分类的名字
            String category1Name = category2List1.get(0).getCategory1Name();
            category1.put("categoryName", category1Name);


            // 声明二级分类集合
            ArrayList<JSONObject> category2Child  = new ArrayList<>();

            // 根据category2Id进行分类
            Map<Long, List<BaseCategoryView>> category2Map = category2List1.stream()
                    .collect(Collectors.groupingBy(BaseCategoryView::getCategory2Id));

            for (Map.Entry<Long, List<BaseCategoryView>> entry2 : category2Map.entrySet()) {
                // 获取二级分类id
                Long category2Id = entry2.getKey();

                // 三级分类数据
                List<BaseCategoryView> category3List1 = entry2.getValue();

                // 获取二级分类名字
                String category2Name = category3List1.get(0).getCategory2Name();
                JSONObject category2 = new JSONObject();
                category2.put("categoryId", category2Id);
                category2.put("categoryName", category2Name);
                category2Child.add(category2);

                // 声明三级分类集合
                ArrayList<JSONObject> category3Child = new ArrayList<>();

                // 循环三级分类数据
                category3List1.stream().forEach(t -> {
                    JSONObject category3 = new JSONObject();
                    category3.put("categoryId", t.getCategory3Id());
                    category3.put("categoryName", t.getCategory3Name());
                    category3Child.add(category3);
                });

                // 将三级数据放到二级里面
                category2.put("categoryChild", category3Child);
            }

            // 将二级数据放一级里面
            category1.put("categoryChild", category2Child);
            list.add(category1);
        }

        return list;
    }

    @Override
    public BaseTrademark getTrademarkByTmId(Long tmId) {
        return baseTrademarkMapper.selectById(tmId);
    }

    @Override
    public List<BaseAttrInfo> getAttrList(Long skuId) {
        return baseAttrInfoMapper.selectBaseAttrInfoListBySkuId(skuId);
    }

    private List<BaseAttrValue> getAttrValueList(long attrId) {
        QueryWrapper<BaseAttrValue> wrapper = new QueryWrapper<>();
        wrapper.eq("attr_id", attrId);
        return baseAttrValueMapper.selectList(wrapper);
    }
}
