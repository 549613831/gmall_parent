package com.atguigu.gmall.user.service;

import com.atguigu.gmall.model.user.UserAddress;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface UserAddressService extends IService<UserAddress> {
    /**
     * 根据用户Id 查询用户的收货地址列表！
     * @param userId 用户id
     * @return 收货地址列表
     */
    List<UserAddress> findUserAddressListByUserId(String userId);
}
