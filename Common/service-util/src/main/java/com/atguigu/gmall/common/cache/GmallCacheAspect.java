package com.atguigu.gmall.common.cache;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.atguigu.gmall.common.constant.RedisConst;
import org.apache.commons.lang.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import springfox.documentation.spring.web.json.Json;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

@Component
@Aspect
public class GmallCacheAspect {

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    @Around("@annotation(com.atguigu.gmall.common.cache.GmallCache)")
    public Object cacheAroundAdvice(ProceedingJoinPoint point) {
        Object result = null;
        RLock lock = null;
        try {
            // 获取到传递的参数
            Object[] args = point.getArgs();
            // 获取方法上的签名
            MethodSignature signature = (MethodSignature) point.getSignature();
            // 得到注解
            GmallCache gmallCache = signature.getMethod().getAnnotation(GmallCache.class);
            // 获取前缀
            String prefix = gmallCache.prefix();
            // 定义缓存key
            String key = prefix + Arrays.asList(args).toString();
            // 查询缓存，看缓存是否有数据
            result = cacheHit(signature, key);

            if (result != null) {
                // 缓存不为空，则将结果返回
                return result;
            }

            // 缓存为空，则查询数据库
            lock = redissonClient.getLock(key + ":lock");

            boolean res = lock.tryLock(100, 10, TimeUnit.SECONDS);
            if (res) {
                // 如果能够获得锁  获取业务数据
                result = point.proceed(point.getArgs());
                if (result == null) {
                    // 如果不能从数据库取到数据，则创建一个空对象放入缓存中，防止缓存穿透
                    Object o = new Object();
                    redisTemplate.opsForValue().set(key, JSONObject.toJSONString(o), RedisConst.SKUKEY_TIMEOUT, TimeUnit.SECONDS);

                    return o;
                }

                // 如果能从数据库中取到数据，则将数据放入到缓存中并返回
                redisTemplate.opsForValue().set(key, JSONObject.toJSONString(result), RedisConst.SKUKEY_TIMEOUT, TimeUnit.SECONDS);

                return result;
            } else {
                // 没有获取到锁，则等待一秒后访问缓存获取数据的方法
                Thread.sleep(1000);
                return cacheHit(signature, key);
            }
        } catch (Throwable e) {
            e.printStackTrace();
        } finally {
            if (lock != null) {
                lock.unlock();
            }
        }

        return result;
    }

    private Object cacheHit(MethodSignature signature, String key) {
        // 获取数据 redis 的 String数据类型 ： key，value 都是字符串
        String result = (String) redisTemplate.opsForValue().get(key);
        // 判断从缓存中获取的数据是否为空
        if (StringUtils.isNotBlank(result)) {
            // 有数据，获取返回类型
            Class returnType = signature.getReturnType();
            // 将数据进行类型转化
            return JSONObject.parseObject(result, returnType);
        }
        return null;
    }


//    @Around("@annotation(com.atguigu.gmall.common.cache.GmallCache)")
//    public Object cacheAroundAdvice(ProceedingJoinPoint point){
//
//        /*
//        1.  获取参数列表
//        2.  获取方法上的注解
//        3.  获取前缀
//        4.  获取目标方法的返回值
//         */
//        Object result = null;
//        try {
//            Object[] args = point.getArgs();
//            System.out.println("gmallCache:"+args);
//            MethodSignature signature = (MethodSignature) point.getSignature();
//            GmallCache gmallCache = signature.getMethod().getAnnotation(GmallCache.class);
//            // 前缀
//            String prefix = gmallCache.prefix();
//            // 从缓存中获取数据
//            String key = prefix+Arrays.asList(args).toString();
//
//            // 获取缓存数据
//            result = cacheHit(signature, key);
//            if (result!=null){
//                // 缓存有数据
//                return result;
//            }
//            // 初始化分布式锁
//            RLock lock = redissonClient.getLock(key);
//            boolean flag = lock.tryLock(100, 100, TimeUnit.SECONDS);
//            if (flag){
//               try {
//                   try {
//                       result = point.proceed(point.getArgs());
//                       // 防止缓存穿透
//                       if (null==result){
//                           // 并把结果放入缓存
//                           Object o = new Object();
//                           this.redisTemplate.opsForValue().set(key, JSONObject.toJSONString(o));
//                           return null;
//                       }
//                   } catch (Throwable throwable) {
//                       throwable.printStackTrace();
//                   }
//                   // 并把结果放入缓存
//                   this.redisTemplate.opsForValue().set(key, JSONObject.toJSONString(result));
//                   return result;
//               }catch (Exception e){
//                   e.printStackTrace();
//               }finally {
//                   // 释放锁
//                   lock.unlock();
//               }
//            }
//        }catch (Exception e){
//            e.printStackTrace();
//        }
//        //boolean flag = lock.tryLock(10L, 10L, TimeUnit.SECONDS);
//        return result;
//    }
//    // 获取缓存数据
//    private Object cacheHit(MethodSignature signature, String key) {
//        // 1. 查询缓存
//        String cache = (String)redisTemplate.opsForValue().get(key);
//        if (StringUtils.isNotBlank(cache)) {
//            // 有，则反序列化，直接返回
//            Class returnType = signature.getReturnType(); // 获取方法返回类型
//            // 不能使用parseArray<cache, T>，因为不知道List<T>中的泛型
//            return JSONObject.parseObject(cache, returnType);
//        }
//        return null;
//    }

}
