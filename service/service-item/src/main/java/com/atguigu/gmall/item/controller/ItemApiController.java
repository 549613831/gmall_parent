package com.atguigu.gmall.item.controller;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.item.service.ItemService;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * @Author 伊塔
 * @Date 16:50 2020/4/24
 */
@RestController
@RequestMapping("api/item")
public class ItemApiController {
    @Autowired
    private ItemService itemService;

    @ApiOperation(value = "调用service-product接口获取商品详情")
    @GetMapping("{skuId}")
    public Result<Map<String,Object>> getItem(@PathVariable long skuId) {
        Map<String,Object> map = itemService.getBySkuId(skuId);

        return Result.ok(map);
    }
}
