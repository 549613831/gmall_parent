package com.atguigu.gmall.user.controller;


import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.model.user.UserAddress;
import com.atguigu.gmall.user.service.UserAddressService;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/user")
public class UserController {
    @Autowired
    private UserAddressService userAddressService;

    @ApiOperation("获取用户地址")
    @GetMapping("inner/findUserAddressListByUserId/{userId}")
    public List<UserAddress> findUserAddressListByUserId(@PathVariable("userId") String userId){
        return userAddressService.findUserAddressListByUserId(userId);
    }

    @ApiOperation("编辑方法")
    @GetMapping("inner/updateUserAddressById/{Id}")
    public Result<Object> updateUserAddressById(@PathVariable Long Id){
        UserAddress userAddress = new UserAddress();
        userAddress.setId(Id);
        userAddressService.updateById(userAddress);
        return Result.ok();
    }
    @ApiOperation("删除方法")
    @GetMapping("inner/removeUserAddressById/{Id}")
    public Result<Object> removeUserAddressById(@PathVariable Long Id){
        userAddressService.removeById(Id);
        return Result.ok();
    }
}
