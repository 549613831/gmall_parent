package com.atguigu.gmall.all.controller;


import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.list.client.ListFeignClient;
import com.atguigu.gmall.model.list.SearchParam;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 产品列表接口
 * </p>
 */
@Controller
@RequestMapping
public class ListController {

    @Autowired
    private ListFeignClient listFeignClient;

    /**
     * 列表搜索
     *
     * @param searchParam 搜索参数
     * @return 搜索结果
     */
    @GetMapping("list.html")
    public String search(SearchParam searchParam, Model model) {
        Result<Map> result = listFeignClient.list(searchParam);

        // 拼接url
        String urlParam = makeUrlParam(searchParam);
        // 品牌属性处理
        String trademarkParam = makeTrademark(searchParam.getTrademark());
        // 平台属性处理
        List<Map<String, String>> propsList = makeProps(searchParam.getProps());
        // 获取排序规则
        Map<String, Object> orderMap = getOrder(searchParam.getOrder());

        model.addAllAttributes(result.getData());
        model.addAttribute("searchParam", searchParam);
        model.addAttribute("urlParam", urlParam);
        model.addAttribute("trademarkParam", trademarkParam);
        model.addAttribute("propsParamList", propsList);
        model.addAttribute("orderMap", orderMap);

        return "list/index";
    }

    private List<Map<String, String>> makeProps(String[] props) {
        ArrayList<Map<String, String>> list = new ArrayList<>();
        // 2:v:n
        if (props != null && props.length > 0) {
            for (String prop : props) {
                String[] split = prop.split(":");
                if (split.length == 3) {
                    HashMap<String, String> map = new HashMap<>();
                    map.put("attrId", split[0]);
                    map.put("attrValue", split[1]);
                    map.put("attrName", split[2]);
                    list.add(map);
                }
            }
        }
        return list;
    }

    /**
     * 处理品牌条件回显
     * @param trademark 品牌属性
     * @return url
     */
    private String makeTrademark(String trademark) {
        if (!StringUtils.isEmpty(trademark) && trademark.length()>0) {
            String[] split = trademark.split(":");
            if (split.length == 2) {
                return "品牌:" + split[1];
            }
        }
        return "";
    }

    /**
     * 拼接url
     *
     * @param searchParam 搜索参数
     * @return url
     */
    private String makeUrlParam(SearchParam searchParam) {
        StringBuilder urlParam = new StringBuilder();

        // 判断关键字
        if (searchParam.getKeyword() != null) {
            urlParam.append("keyword=").append(searchParam.getKeyword());
        }

        // 判断分类属性
        if (searchParam.getCategory1Id() != null) {
            urlParam.append("category1Id=").append(searchParam.getCategory1Id());
        }

        if (searchParam.getCategory2Id() != null) {
            urlParam.append("category2Id=").append(searchParam.getCategory2Id());
        }

        if (searchParam.getCategory3Id() != null) {
            urlParam.append("category3Id=").append(searchParam.getCategory3Id());
        }

        // 判断品牌
        if (searchParam.getTrademark() != null && urlParam.length() > 0) {
            urlParam.append("&trademark=").append(searchParam.getTrademark());
        }

        // 判断平台属性
        if (searchParam.getProps() != null) {
            for (String prop : searchParam.getProps()) {
                if (urlParam.length() > 0) {
                    urlParam.append("&props=").append(prop);
                }
            }
        }

        return "list.html?" + urlParam.toString();
    }

    /**
     * 获取排序规则 http://list.gmall.com/list.html?category3Id=61&order=2:desc
     *
     * @param order order
     * @return 排序规则
     */
    private Map<String, Object> getOrder(String order) {
        HashMap<String, Object> map = new HashMap<>();
        if (StringUtils.isNotEmpty(order)) {
            String[] split = order.split(":");
            if (split.length == 2) {
                // 传递的哪个字段
                map.put("type", split[0]);
                // sort代表排序方式
                map.put("sort", split[1]);
            }
        } else {
            // 如果没有指定排序规则，则默认
            map.put("type", "1");
            // sort代表排序方式
            map.put("sort","asc");
        }
        return map;
    }
}
