package com.atguigu.gmall.gateway.filter;

import com.alibaba.fastjson.JSONObject;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.common.result.ResultCodeEnum;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;

@Component
public class AuthGlobalFilter implements GlobalFilter {
    @Autowired
    private RedisTemplate redisTemplate;
    @Value("${authUrls.url}")
    private String authUrls;
    /**
     * antPathMatcher:匹配路径工具类
     */
    private AntPathMatcher antPathMatcher = new AntPathMatcher();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 获取请求对象
        ServerHttpRequest request = exchange.getRequest();
        // 获取url
        String path = request.getURI().getPath();

        if (antPathMatcher.match("/**/inner/**", path)) {
            // 内部接口，不允许访问
            ServerHttpResponse response = exchange.getResponse();
            return out(response, ResultCodeEnum.PERMISSION);
        }

        String userId = getUserId(request);
        if (antPathMatcher.match("/api/**/auth/**", path)) {
            // api接口，需要登录后才能进行访问，先获取用户id
            if (StringUtils.isEmpty(userId)) {
                // 用户不存在
                ServerHttpResponse response = exchange.getResponse();
                return out(response, ResultCodeEnum.LOGIN_AUTH);
            }
        }

        // 验证url是否属于需要登录才能访问
        for (String authUrl : authUrls.split(",")) {
            if (path.contains(authUrl) && StringUtils.isEmpty(userId)) {
                // url访问的控制器需要登录，但现在用户未登录
                ServerHttpResponse response = exchange.getResponse();
                // 303状态码表示由于请求对应的资源存在着另一个URI，应使用重定向获取请求的资源
                response.setStatusCode(HttpStatus.SEE_OTHER);
                response.getHeaders().set(HttpHeaders.LOCATION, "http://www.gmall.com/login.html?originUrl=" + request.getURI());
                // 重定向到登录
                return response.setComplete();
            }
        }

        // 设置网关请求头，将userId传递给后端
        try {
            String userTempId = this.getUserTempId(request);

            if (!StringUtils.isEmpty(userId) || !StringUtils.isEmpty(userTempId)) {
                // 用户id或临时id存在任意一个即可
                if (!StringUtils.isEmpty(userId)) {
                    request.mutate().header("userId", userId).build();
                }

                if (!StringUtils.isEmpty(userTempId)) {
                    request.mutate().header("userTempId", userTempId);
                }

                // 将现在的request 变成 exchange对象
                return chain.filter(exchange.mutate().request(request).build());
            }

        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        return chain.filter(exchange);
    }

    /**
     * 接口鉴权失败返回数据
     *
     * @param response       response
     * @param resultCodeEnum permission
     * @return 返回数据
     */
    private Mono<Void> out(ServerHttpResponse response, ResultCodeEnum resultCodeEnum) {
        // 返回用户没有权限登录
        Result<Object> result = Result.build(null, resultCodeEnum);
        // 将结果转成json格式
        String jsonString = JSONObject.toJSONString(result);
        // 将json串转成字节数组
        byte[] bytes = jsonString.getBytes(StandardCharsets.UTF_8);
        // 声明一个DataBuffer
        DataBuffer wrap = response.bufferFactory().wrap(bytes);
        // 设置信息输出格式
        response.getHeaders().add("Content-Type", "application/json;charset=UTF-8");
        // 将信息输入到页面
        return response.writeWith(Mono.just(wrap));
    }

    /**
     * 获取用户id
     *
     * @param request request
     * @return 用户id
     */
    private String getUserId(ServerHttpRequest request) {
        String token = "";
        // 从header 中获取token数据
        List<String> tokenList = request.getHeaders().get("token");

        if (tokenList != null && !tokenList.isEmpty()) {
            token = tokenList.get(0);
        } else {
            MultiValueMap<String, HttpCookie> cookies = request.getCookies();
            HttpCookie cookie = cookies.getFirst("token");
            if (cookie != null) {
                token = URLDecoder.decode(cookie.getValue());
            }
        }

        // 如果获取到token，则从获取中获取userId
        if (!StringUtils.isEmpty(token)) {
            return (String) redisTemplate.opsForValue().get("user:login:" + token);
        }

        return "";
    }

    /**
     * 获取当前用户临时用户id
     * @param request request
     * @return 临时用户id
     */
    private String getUserTempId(ServerHttpRequest request) throws UnsupportedEncodingException {
        // 从header里获取用户id
        String userTempId = "";
        userTempId = request.getHeaders().getFirst("userTempId");
        if (userTempId != null && userTempId.length() > 0) {
            return userTempId;
        } else {
            // header中没有用户id，从cookie中获取
            HttpCookie cookie = request.getCookies().getFirst("userTempId");
            if (cookie != null) {
                userTempId = URLDecoder.decode(cookie.getValue(), "UTF-8");
                return userTempId;
            }
        }

        return userTempId;
    }

}
