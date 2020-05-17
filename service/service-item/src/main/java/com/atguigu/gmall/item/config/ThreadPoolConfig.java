package com.atguigu.gmall.item.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @Author 伊塔
 * @Description 线程池配置类
 * @Date 20:39 2020/4/27
 */
@Configuration
public class ThreadPoolConfig {
    @Bean
    public ThreadPoolExecutor threadPoolExecutor() {
        /**
         * 核心线程数
         * 拥有最多线程数
         * 空闲线程的存活时间
         * 存活时间单位
         * 用于缓存任务的阻塞队列
         * 指定创建线程池的工厂
         * 当队列满时，且线程数满时，线程池拒绝新任务的策略
         */
        return new ThreadPoolExecutor(
                50,
                500,
                50,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(10000),
                Executors.defaultThreadFactory(),
                new ThreadPoolExecutor.DiscardOldestPolicy());
    }
}
