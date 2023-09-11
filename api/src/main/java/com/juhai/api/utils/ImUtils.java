package com.juhai.api.utils;

import cn.hutool.core.net.url.UrlQuery;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class ImUtils {
    public static JSONObject post(String url, String appKey, String appSecret, Map<String, Object> params) throws Exception {
        HttpRequest post = HttpUtil.createPost(url);
        post.header("AppKey", appKey);
        String nonce = RandomUtil.randomString(20);
        post.header("Nonce", nonce);
        String CurTime = String.valueOf(new Date().getTime() / 1000);
        post.header("CurTime", CurTime);
        post.header("CheckSum", CheckSumBuilder.getCheckSum(appSecret, nonce, CurTime));
        post.header("Content-Type", "application/x-www-form-urlencoded;charset=utf-8");
        String s = UrlQuery.of(params).toString();
        post.body(s);

        log.info("请求参数:" + post.toString());
        HttpResponse response = post.execute();
        JSONObject body = JSONObject.parseObject(response.body());
        log.info("请求响应:" + body);
        if (body == null) {
            throw new Exception("请求调用异常");
        }
        if (body.getIntValue("code") != 200) {
            throw new Exception(body.getString("desc"));
        }
        return body;
    }


    public static JSONObject postTx(String url,JSONObject params) throws Exception {
        HttpRequest post = HttpUtil.createPost(url);
        post.body(params.toString());

        log.info("请求参数:" + post.toString());
        HttpResponse response = post.execute();
        JSONObject body = JSONObject.parseObject(response.body());
        log.info("请求响应:" + body);
        if (body == null) {
            throw new Exception("请求调用异常");
        }
        if (body.getIntValue("ErrorCode") != 0) {
            throw new Exception(body.getString("ErrorInfo"));
        }
        return body;
    }

    public static void main(String[] args) {
        Map<String, Object> params = new HashMap<>();
        params.put("sdkappid", "222");
        params.put("identifier", "identifier");
        params.put("usersig", "usersig");
        params.put("random", RandomUtil.randomLong(1, 429496729));
        params.put("contenttype", "json");
        System.out.println(UrlQuery.of(params));
    }
}
