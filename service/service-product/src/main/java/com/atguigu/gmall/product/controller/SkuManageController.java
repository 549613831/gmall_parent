package com.atguigu.gmall.product.controller;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.model.product.SkuInfo;
import com.atguigu.gmall.model.product.SpuImage;
import com.atguigu.gmall.model.product.SpuSaleAttr;
import com.atguigu.gmall.product.service.ManagerService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.ibatis.jdbc.Null;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @Author 伊塔
 * @Date 19:52 2020/4/22
 */
@Api(tags = "商品SKU接口")
@RestController
@RequestMapping("admin/product")
public class SkuManageController {
    @Autowired
    private ManagerService manageService;

    @ApiOperation(value = "获取图片数据")
    @GetMapping("spuImageList/{spuId}")
    public Result<List<SpuImage>> getSpuImageList(@PathVariable("spuId") Long spuId) {
        List<SpuImage> spuImageList = manageService.getSpuImageList(spuId);
        return Result.ok(spuImageList);
    }

    @ApiOperation(value = "加载销售属性")
    @GetMapping("spuSaleAttrList/{spuId}")
    public Result<List<SpuSaleAttr>> getSpuSaleAttrList(@PathVariable("spuId") Long spuId) {
        List<SpuSaleAttr> spuSaleAttrList = manageService.getSpuSaleAttrList(spuId);
        return Result.ok(spuSaleAttrList);
    }

    @ApiOperation(value = "保存sku信息")
    @PostMapping("saveSkuInfo")
    public Result<Null> saveSkuInfo(@RequestBody SkuInfo skuInfo) {
        manageService.saveSkuInfo(skuInfo);
        return Result.ok();
    }

    @ApiOperation(value = "sku分页")
    @GetMapping("list/{page}/{size}")
    public Result<IPage<SkuInfo>> skuInfoList(@PathVariable long page,
                                             @PathVariable long size) {
        Page<SkuInfo> skuInfoPage = new Page<>(page, size);
        IPage<SkuInfo> listPage = manageService.selectPage(skuInfoPage);
        return Result.ok(listPage);
    }

    @ApiOperation(value = "商品上架")
    @GetMapping("onSale/{skuId}")
    public Result<Null> onSale(@PathVariable("skuId") Long skuId) {
        manageService.onSale(skuId);
        return Result.ok();
    }

    @ApiOperation(value = "商品下架")
    @GetMapping("cancelSale/{skuId}")
    public Result<Null> cancelSale(@PathVariable("skuId") Long skuId) {
        manageService.cancelSale(skuId);
        return Result.ok();
    }


}
