package com.atguigu.gmall.user.controller;

import com.atguigu.gmall.common.constant.RedisConst;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.model.user.UserInfo;
import com.atguigu.gmall.user.service.UserService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/user/passport")
public class PassportController {
    @Autowired
    private UserService userService;
    @Autowired
    private RedisTemplate redisTemplate;

    @ApiOperation("登录")
    @PostMapping("login")
    public Result<Object> login(@RequestBody UserInfo userInfo) {
        UserInfo info = userService.login(userInfo);
        if (info != null) {
            // 用户信息存在数据库中
            String token = UUID.randomUUID().toString().replace("-", "");
            HashMap<String, Object> map = new HashMap<>();
            map.put("name", info.getName());
            map.put("nickName", info.getNickName());
            map.put("token", token);

            // 将数据放入缓存
            redisTemplate.opsForValue().set(
                    RedisConst.USER_LOGIN_KEY_PREFIX + token,
                    info.getId().toString(),
                    RedisConst.USERKEY_TIMEOUT,
                    TimeUnit.SECONDS);

            return Result.ok(map);
        } else {
            // 用户不存在
            return Result.fail().message("用户名或密码不正确");
        }
    }

    @ApiOperation("退出登录")
    @GetMapping("logout")
    public Result<Object> logout(HttpServletRequest request) {
        // 删除缓存中的数据
        String token = request.getHeader("token");
        redisTemplate.delete(RedisConst.USER_LOGIN_KEY_PREFIX + token);
        return Result.ok();
    }

}
