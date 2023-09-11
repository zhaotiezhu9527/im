package com.juhai.api.controller;

import cn.hutool.core.map.MapUtil;
import cn.hutool.core.net.url.UrlQuery;
import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.extra.servlet.ServletUtil;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.juhai.api.controller.request.LoginRequest;
import com.juhai.api.controller.request.UpdatePwdRequest;
import com.juhai.api.controller.request.UserRegisterRequest;
import com.juhai.api.utils.ImUtils;
import com.juhai.api.utils.JwtUtils;
import com.juhai.commons.entity.Avatar;
import com.juhai.commons.entity.User;
import com.juhai.commons.entity.UserLog;
import com.juhai.commons.service.AvatarService;
import com.juhai.commons.service.ParamterService;
import com.juhai.commons.service.UserLogService;
import com.juhai.commons.service.UserService;
import com.juhai.commons.utils.MsgUtil;
import com.juhai.commons.utils.R;
import com.juhai.commons.utils.RedisKeyUtil;
import com.tencentyun.TLSSigAPIv2;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Api(value = "用户相关", tags = "用户")
@RequestMapping("/user")
@RestController
public class UserController {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private UserService userService;

    @Autowired
    private UserLogService userLogService;

    @Autowired
    private ParamterService paramterService;

    @Value("${token.expire}")
    private int expire;

    @Value("${im.url}")
    private String imUrl;

    @Value("${im.appkey}")
    private String appkey;

    @Value("${im.appSecret}")
    private String appSecret;

    @Value("${im.plat}")
    private String plat;

    @Value("${IMConfig.txUrl}")
    private String txUrl;
    @Value("${IMConfig.sdkAppId}")
    private long sdkAppId;

    @Value("${IMConfig.secretKey}")
    private String secretKey;

    @Value("${IMConfig.identifier}")
    private String identifier;

    @Autowired
    private AvatarService avatarService;

    @Transactional(rollbackFor = Exception.class)
    @ApiOperation(value = "注册-网易")
    @PostMapping("/register")
    public R register(@Validated UserRegisterRequest request, HttpServletRequest httpServletRequest) throws Exception {
        String userName = request.getUserName().toLowerCase();
        String clientIP = ServletUtil.getClientIPByHeader(httpServletRequest, "x-original-forwarded-for");
        // 查询用户名是否存在
        long exist = userService.count(new LambdaQueryWrapper<User>().eq(User::getUserName, userName));
        if (exist > 0) {
            return R.error(MsgUtil.get("system.user.register.exist"));
        }

        Map<String, String> allParamByMap = paramterService.getAllParamByMap();
        String resourceDomain = allParamByMap.get("resource_domain");

        // 随机获取一个头像
        List<Avatar> list = avatarService.list();
        Collections.shuffle(list);

        String imUserName = plat + userName;
        Date now = new Date();
        User user = new User();
        user.setUserName(userName);
        user.setNickName(request.getUserName());
        user.setLoginPwd(SecureUtil.md5(request.getLoginPwd()));
        user.setStatus(1);
        user.setRegisterTime(now);
        user.setRegisterIp(clientIP);
        user.setLastTime(now);
        user.setLastIp(clientIP);
        user.setModifyTime(now);
        user.setImUserName(imUserName);
        userService.save(user);

        // 调用网易创建用户
        Map<String, Object> param = new HashMap<>();
        param.put("accid", imUserName);
        param.put("name", userName);
        param.put("icon", resourceDomain + list.get(0).getImgUrl());
        param.put("gender", 0);
        JSONObject imJson = ImUtils.post(imUrl + "/nimserver/user/create.action", appkey, appSecret, param);
        JSONObject infoJson = imJson.getJSONObject("info");
        String imToken = infoJson.getString("token");
        userService.update(
                new UpdateWrapper<User>().lambda()
                        .set(User::getImToken, imToken)
                        .eq(User::getUserName, request.getUserName()));

        // 添加默认客服为好友
        String kefu = allParamByMap.get("kefu");
        if (StringUtils.isNotBlank(kefu) && !StringUtils.equals(kefu, userName)) {
            User kefuUser = userService.getUserByName(kefu);
            if (kefuUser != null) {
                Map<String, Object> addParam = new HashMap<>();
                addParam.put("accid", plat + userName);
                addParam.put("faccid", plat + kefu);
                addParam.put("type", 1);
                ImUtils.post(imUrl + "/nimserver/friend/add.action", appkey, appSecret, addParam);
                // 发送问候语
                String welcome = allParamByMap.get("welcome");
                if (StringUtils.isNotBlank(welcome)) {
                    ThreadUtil.execAsync(new Runnable() {
                        @Override
                        public void run() {
                            Map<String, Object> messageParam = new HashMap<>();
                            messageParam.put("from", plat + kefu);
                            messageParam.put("ope", 0);
                            messageParam.put("to", plat + userName);
                            messageParam.put("type", 0);
                            JSONObject messageObj = new JSONObject();
                            messageObj.put("msg", welcome);
                            messageParam.put("body", messageObj.toString());
                            try {
                                ImUtils.post(imUrl + "/nimserver/msg/sendMsg.action", appkey, appSecret, messageParam);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }
                    });
                }
            }
        }

        // 登录日志
        UserLog log = new UserLog();
        log.setUserName(userName);
        log.setIp(clientIP);
        log.setIpDetail(null);
        log.setLoginTime(new Date());
        userLogService.save(log);

        /** 保存token **/
        Map<String, String> map = new HashMap<>();
        map.put("userName", userName);
        map.put("userIp", clientIP);
        map.put("random", RandomUtil.randomString(6));
        String token = JwtUtils.getToken(map);
        redisTemplate.opsForValue().set(RedisKeyUtil.UserTokenKey(userName), token, expire, TimeUnit.MINUTES);

        return R.ok().put("token", token).put("imToken", imToken).put("accid", plat + userName);
    }


    @Transactional(rollbackFor = Exception.class)
    @ApiOperation(value = "注册-腾讯")
    @PostMapping("/register/v2")
    public R registerV2(@Validated UserRegisterRequest request, HttpServletRequest httpServletRequest) throws Exception {
        String userName = request.getUserName().toLowerCase();
        String clientIP = ServletUtil.getClientIPByHeader(httpServletRequest, "x-original-forwarded-for");
        // 查询用户名是否存在
        long exist = userService.count(new LambdaQueryWrapper<User>().eq(User::getUserName, userName));
        if (exist > 0) {
            return R.error(MsgUtil.get("system.user.register.exist"));
        }

        Map<String, String> allParamByMap = paramterService.getAllParamByMap();
        String resourceDomain = allParamByMap.get("resource_domain");

        // 随机获取一个头像
        List<Avatar> list = avatarService.list();
        Collections.shuffle(list);

        String imUserName = userName;
        Date now = new Date();
        User user = new User();
        user.setUserName(userName);
        user.setNickName(request.getUserName());
        user.setLoginPwd(SecureUtil.md5(request.getLoginPwd()));
        user.setStatus(1);
        user.setRegisterTime(now);
        user.setRegisterIp(clientIP);
        user.setLastTime(now);
        user.setLastIp(clientIP);
        user.setModifyTime(now);
        user.setImUserName(imUserName);
        userService.save(user);

        // 调用腾讯创建用户
        String usersig = new TLSSigAPIv2(sdkAppId, secretKey).genUserSig(identifier, 60);
        String createUserUrl = txUrl + "/v4/im_open_login_svc/account_import?" + getImPath(usersig);

        JSONObject createUser = new JSONObject();
        createUser.put("UserID", userName);
        createUser.put("Nick", userName);
        createUser.put("FaceUrl", resourceDomain + list.get(0).getImgUrl());
        ImUtils.postTx(createUserUrl, createUser);

        // 添加默认客服为好友
        String kefu = allParamByMap.get("kefu");
        if (StringUtils.isNotBlank(kefu) && !StringUtils.equals(kefu, userName)) {
            User kefuUser = userService.getUserByName(kefu);
            if (kefuUser != null) {
                String addFriendUrl = txUrl + "/v4/sns/friend_add?" + getImPath(usersig);
                JSONObject addFriend = new JSONObject();
                addFriend.put("From_Account", kefu);

                JSONArray addFriendItem = new JSONArray();
                JSONObject item = new JSONObject();
                item.put("To_Account", userName);
                item.put("AddSource", "AddSource_Type_SYSTEM");
                addFriendItem.add(item);
                addFriend.put("AddFriendItem", addFriendItem);

                addFriend.put("ForceAddFlags", 1);
                ImUtils.postTx(addFriendUrl, addFriend);
            }
        }

        // 登录日志
        UserLog log = new UserLog();
        log.setUserName(userName);
        log.setIp(clientIP);
        log.setIpDetail(null);
        log.setLoginTime(new Date());
        userLogService.save(log);

        /** 保存token **/
        Map<String, String> map = new HashMap<>();
        map.put("userName", userName);
        map.put("userIp", clientIP);
        map.put("random", RandomUtil.randomString(6));
        String token = JwtUtils.getToken(map);
        redisTemplate.opsForValue().set(RedisKeyUtil.UserTokenKey(userName), token, expire, TimeUnit.MINUTES);

        long expire = 60 * 60 * 24 * 180;
        String usersig1 = new TLSSigAPIv2(sdkAppId, secretKey).genUserSig(userName, expire);
        redisTemplate.opsForValue().set(RedisKeyUtil.UserIMKey(userName), usersig1, expire, TimeUnit.SECONDS);
        return R.ok().put("token", token).put("usersig", usersig1);
    }

    private String getImPath(String usersig) {
        Map<String, Object> params = new HashMap<>();
        params.put("sdkappid", sdkAppId);
        params.put("identifier", identifier);
        params.put("usersig", usersig);
        params.put("random", RandomUtil.randomLong(1, 429496729));
        params.put("contenttype", "json");
        return UrlQuery.of(params).toString();
    }

    @ApiOperation(value = "退出登录")
    @PostMapping("/logout")
    public R logout(HttpServletRequest httpServletRequest) {
        String userName = JwtUtils.getUserName(httpServletRequest);
        redisTemplate.delete(RedisKeyUtil.UserTokenKey(userName));
        return R.ok();
    }

    @ApiOperation(value = "检测腾讯sign失效")
    @GetMapping("/checkSign")
    public R checkSign(HttpServletRequest httpServletRequest) {
        String userName = JwtUtils.getUserName(httpServletRequest);
        Boolean hasKey = redisTemplate.hasKey(RedisKeyUtil.UserIMKey(userName));
        return R.ok().put("valid", hasKey);
    }

    @ApiOperation(value = "登录-网易")
    @PostMapping("/login")
    public R login(@Validated LoginRequest request, HttpServletRequest httpServletRequest) {
        String userName = request.getUserName().toLowerCase();
        String clientIP = ServletUtil.getClientIPByHeader(httpServletRequest, "x-original-forwarded-for");

        // 查询用户信息
        User user = userService.getUserByName(userName);
        if (user == null) {
            return R.error(MsgUtil.get("system.user.login.noexist"));
        }

        if (user.getStatus().intValue() == 0) {
            return R.error(MsgUtil.get("system.user.enable"));
        }

        // 获取所有参数配置
        Map<String, String> paramsMap = paramterService.getAllParamByMap();

        String incKey = RedisKeyUtil.LoginPwdErrorKey(userName);
        /** 每日错误次数上限 **/
        String dayCount = redisTemplate.opsForValue().get(incKey);
        int count = NumberUtils.toInt(dayCount, 0);
        Integer pwdErrCount = MapUtil.getInt(paramsMap, "login_pwd_error", 0);
        if (pwdErrCount > 0 && count >= pwdErrCount) {
            return R.error(MsgUtil.get("system.user.login.pwd.limit"));
        }

        // 验证密码正确
        String pwd = SecureUtil.md5(request.getLoginPwd());
        if (!StringUtils.equals(pwd, user.getLoginPwd())) {
            /** 累计密码错误 **/
            redisTemplate.opsForValue().increment(incKey);
            redisTemplate.expire(incKey, 1, TimeUnit.DAYS);
            return R.error(MsgUtil.get("system.user.login.pwd.error"));
        }

        Date now = new Date();
        /** 更新最后登录时间 **/
        userService.update(
                new UpdateWrapper<User>().lambda()
                        .eq(User::getId, user.getId())
                        .set(User::getLastIp, clientIP)
                        .set(User::getLastTime, now)
        );

        // 登录日志
        UserLog log = new UserLog();
        log.setUserName(userName);
        log.setIp(clientIP);
        log.setIpDetail(null);
        log.setLoginTime(new Date());
        userLogService.save(log);

        /** 保存token **/
        Map<String, String> map = new HashMap<>();
        map.put("userName", userName);
        map.put("userIp", clientIP);
        map.put("random", RandomUtil.randomString(6));
        String token = JwtUtils.getToken(map);
        redisTemplate.opsForValue().set(RedisKeyUtil.UserTokenKey(user.getUserName()), token, expire, TimeUnit.MINUTES);

        /** 删除密码输入错误次数 **/
        redisTemplate.delete(incKey);
        return R.ok().put("token", token).put("imToken", user.getImToken()).put("accid", plat + userName);
    }


    @ApiOperation(value = "登录-腾讯")
    @PostMapping("/login/V2")
    public R loginV2(@Validated LoginRequest request, HttpServletRequest httpServletRequest) {
        String userName = request.getUserName().toLowerCase();
        String clientIP = ServletUtil.getClientIPByHeader(httpServletRequest, "x-original-forwarded-for");

        // 查询用户信息
        User user = userService.getUserByName(userName);
        if (user == null) {
            return R.error(MsgUtil.get("system.user.login.noexist"));
        }

        if (user.getStatus().intValue() == 0) {
            return R.error(MsgUtil.get("system.user.enable"));
        }

        // 获取所有参数配置
        Map<String, String> paramsMap = paramterService.getAllParamByMap();

        String incKey = RedisKeyUtil.LoginPwdErrorKey(userName);
        /** 每日错误次数上限 **/
        String dayCount = redisTemplate.opsForValue().get(incKey);
        int count = NumberUtils.toInt(dayCount, 0);
        Integer pwdErrCount = MapUtil.getInt(paramsMap, "login_pwd_error", 0);
        if (pwdErrCount > 0 && count >= pwdErrCount) {
            return R.error(MsgUtil.get("system.user.login.pwd.limit"));
        }

        // 验证密码正确
        String pwd = SecureUtil.md5(request.getLoginPwd());
        if (!StringUtils.equals(pwd, user.getLoginPwd())) {
            /** 累计密码错误 **/
            redisTemplate.opsForValue().increment(incKey);
            redisTemplate.expire(incKey, 1, TimeUnit.DAYS);
            return R.error(MsgUtil.get("system.user.login.pwd.error"));
        }

        Date now = new Date();
        /** 更新最后登录时间 **/
        userService.update(
                new UpdateWrapper<User>().lambda()
                        .eq(User::getId, user.getId())
                        .set(User::getLastIp, clientIP)
                        .set(User::getLastTime, now)
        );

        // 登录日志
        UserLog log = new UserLog();
        log.setUserName(userName);
        log.setIp(clientIP);
        log.setIpDetail(null);
        log.setLoginTime(new Date());
        userLogService.save(log);

        /** 保存token **/
        Map<String, String> map = new HashMap<>();
        map.put("userName", userName);
        map.put("userIp", clientIP);
        map.put("random", RandomUtil.randomString(6));
        String token = JwtUtils.getToken(map);
        redisTemplate.opsForValue().set(RedisKeyUtil.UserTokenKey(user.getUserName()), token, expire, TimeUnit.MINUTES);

        /** 删除密码输入错误次数 **/
        redisTemplate.delete(incKey);

        long expire = 60 * 60 * 24 * 180;
        String userSig = new TLSSigAPIv2(sdkAppId, secretKey).genUserSig(userName, expire);
        redisTemplate.opsForValue().set(RedisKeyUtil.UserIMKey(userName), userSig, expire, TimeUnit.SECONDS);
        return R.ok().put("token", token).put("usersig", userSig);
    }

    @ApiOperation(value = "修改用户密码")
    @PostMapping("/updatePwd")
    public R updatePwd(@Validated UpdatePwdRequest request, HttpServletRequest httpServletRequest) {
        String userName = JwtUtils.getUserName(httpServletRequest);

        User user = userService.getUserByName(userName);

        String oldPwd = SecureUtil.md5(request.getOldPwd());
        if (!StringUtils.equals(oldPwd, user.getLoginPwd())) {
            return R.error(MsgUtil.get("system.user.oldpwderror"));
        }

        userService.update(
                new UpdateWrapper<User>().lambda()
                        .set(User::getLoginPwd, SecureUtil.md5(request.getNewPwd()))
                        .set(User::getModifyTime, new Date())
                        .eq(User::getUserName, userName)
        );

        return R.ok();
    }
}
