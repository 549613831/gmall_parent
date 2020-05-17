package com.atguigu.gmall.product.service.impl;

import com.atguigu.gmall.product.service.TestService;
import org.apache.commons.lang.StringUtils;
import org.redisson.api.RLock;
import org.redisson.api.RReadWriteLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class TestServiceImpl implements TestService {
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private RedissonClient redissonClient;

    @Override
    public void testLock() {
        // 只要锁的名称相同，就是同一把锁
        RLock lock = redissonClient.getLock("lock");
        // 加锁，最常使用的方式
        //lock.lock();

        // 加锁时也可以指定过期时间
        //lock.lock(10, TimeUnit.SECONDS);

        // 尝试加锁，最多等待100秒，超过时间后直接获得锁，过期时间为10秒
        try {
            boolean res = lock.tryLock(100, 10, TimeUnit.SECONDS);
            if (res) {
                // 执行业务代码
                String num = redisTemplate.opsForValue().get("num");
                if (StringUtils.isBlank(num)) {
                    return;
                }
                int i = Integer.parseInt(num);
                redisTemplate.opsForValue().set("num", String.valueOf(++i));
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // 解锁，如果指定了过期时间也可以不手动解锁
        //lock.unlock();
    }

    @Override
    public String readLock() {
        RReadWriteLock lock = redissonClient.getReadWriteLock("ReadWriteLock");
        // 获取读锁
        RLock rLock = lock.readLock();

        // 上锁。10秒后自动解锁
        rLock.lock(10, TimeUnit.SECONDS);

        // 获取锁数据并返回
        String msg = redisTemplate.opsForValue().get("msg");

        return msg;
    }

    @Override
    public String writeLock() {
        // 获取读写锁，它们的key必须一致
        RReadWriteLock lock = redissonClient.getReadWriteLock("ReadWriteLock");
        RLock wLock = lock.writeLock();
        wLock.lock(10, TimeUnit.SECONDS);

        redisTemplate.opsForValue().set("msg", UUID.randomUUID().toString());
        return "写入数据完成。。。";
    }
}
