package com.atguigu.gmall.product.controller;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.model.product.*;
import com.atguigu.gmall.product.service.ManagerService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.Api;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @author 伊塔
 */
@Api(tags = "商品基本属性接口")
@RestController
@RequestMapping("admin/product")
public class ManagerController {
    @Autowired
    private ManagerService managerService;

    /**
     * 获取一级分类集合
     */
    @GetMapping("getCategory1")
    public Result<List<BaseCategory1>> getCategory1() {
        List<BaseCategory1> category1 = managerService.getCategory1();
        return Result.ok(category1);
    }

    /**
     * 获取二级分类集合
     */
    @GetMapping("getCategory2/{category1Id}")
    public Result<List<BaseCategory2>> getCategory2(@PathVariable long category1Id) {
        List<BaseCategory2> category2 = managerService.getCategory2(category1Id);
        return Result.ok(category2);
    }

    /**
     * 获取三级分类集合
     */
    @GetMapping("getCategory3/{category2Id}")
    public Result<List<BaseCategory3>> getCategory3(@PathVariable long category2Id) {
        List<BaseCategory3> category3 = managerService.getCategory3(category2Id);
        return Result.ok(category3);
    }

    /**
     * 根据一级分类id，二级分类id，三级分类id获取平台属性数据
     */
    @GetMapping("attrInfoList/{category1Id}/{category2Id}/{category3Id}")
    public Result<List<BaseAttrInfo>> attrInfoList(
            @PathVariable("category1Id") Long category1Id,
            @PathVariable("category2Id") Long category2Id,
            @PathVariable("category3Id") Long category3Id){
        List<BaseAttrInfo> attrInfoList = managerService.getAttrInfoList(category1Id, category2Id, category3Id);
        return Result.ok(attrInfoList);
    }

    /**
     * 保存或修改平台属性
     */
    @PostMapping("saveAttrInfo")
    public Result saveAttrInfo(@RequestBody BaseAttrInfo baseAttrInfo) {
        // 前台数据都被封装到该对象中baseAttrInfo
        managerService.saveAttrInfo(baseAttrInfo);
        return Result.ok();
    }

    /**
     * 通过平台属性id获取平台属性值
     */
    @GetMapping("getAttrValueList/{attrId}")
    public Result<List<BaseAttrValue>> getAttrValueList(@PathVariable long attrId) {
        BaseAttrInfo baseAttrInfo = managerService.getAttrInfo(attrId);
        List<BaseAttrValue> attrValueList = baseAttrInfo.getAttrValueList();
        return Result.ok(attrValueList);
    }

    /**
     * 获取分页信息
     */
    @GetMapping("{page}/{size}")
    public Result<IPage<SpuInfo>> index(
            @PathVariable long page,
            @PathVariable long size,
            SpuInfo spuInfo) {
        Page<SpuInfo> spuInfoPage = new Page<>(page, size);
        IPage<SpuInfo> spuInfoIPage = managerService.selectPage(spuInfoPage, spuInfo);
        return Result.ok(spuInfoIPage);

    }

}
