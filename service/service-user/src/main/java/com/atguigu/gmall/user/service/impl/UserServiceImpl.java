package com.atguigu.gmall.user.service.impl;

import com.atguigu.gmall.model.user.UserInfo;
import com.atguigu.gmall.user.mapper.UserInfoMapper;
import com.atguigu.gmall.user.service.UserService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

/**
 * @Author 伊塔
 * @Date 20:44 2020/5/5
 */
@Service
public class UserServiceImpl implements UserService {
    @Autowired
    private UserInfoMapper userInfoMapper;

    @Override
    public UserInfo login(UserInfo userInfo) {
        // 获取用户输入的密码，进行加密
        String passwd = userInfo.getPasswd();
        String encryption = DigestUtils.md5DigestAsHex(passwd.getBytes());

        QueryWrapper<UserInfo> wrapper = new QueryWrapper<>();
        wrapper.eq("login_name", userInfo.getLoginName());
        wrapper.eq("passwd", encryption);
        UserInfo info = userInfoMapper.selectOne(wrapper);

        if (info != null) {
            // 说明数据库中有当前用户
            return info;
        } else {
            return null;
        }

    }
}
