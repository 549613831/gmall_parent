package com.atguigu.gmall.product.service;

import com.atguigu.gmall.model.product.BaseTrademark;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * @Author 伊塔
 * @Date 20:04 2020/4/21
 */
public interface BaseTrademarkService extends IService<BaseTrademark> {
    /**
     * Banner分页列表
     * @param pageParam 分页条件
     * @return 分页数据
     */
    IPage<BaseTrademark> selectPage(Page<BaseTrademark> pageParam);

}
