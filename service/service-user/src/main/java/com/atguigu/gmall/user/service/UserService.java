package com.atguigu.gmall.user.service;

import com.atguigu.gmall.model.user.UserInfo;

/**
 * @Author 伊塔
 * @Date 20:44 2020/5/5
 */
public interface UserService {
    /**
     * 登录方法，密码需要加密
     * @param userInfo 登录信息
     * @return 用户
     */
    UserInfo login(UserInfo userInfo);

}
