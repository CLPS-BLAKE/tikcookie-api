package com.dss.user.service;

import com.dss.user.model.dto.LoginDTO;
import com.dss.user.model.vo.LoginVO;

/**
 * 认证。规则见接口文档 5.1.1–5.1.3、需求文档第 2 节（账号）；Redis key 见中间件配置第 4 节。
 */
public interface AuthService {

    /**
     * 发送登录验证码。
     * <ul>
     *     <li>60 秒内发过（dss:login:code:interval:{phone} 存在）：101001；</li>
     *     <li>生成 6 位数字验证码，写 dss:login:code:{phone}（300 秒），写重发间隔标记（60 秒），删掉失败计数；</li>
     *     <li>不接真短信：验证码写 INFO 日志（只用于练手）。</li>
     * </ul>
     */
    void sendSmsCode(String phone);

    /**
     * 登录 / 自动注册。
     * <ul>
     *     <li>验证码不存在或不一致：失败计数 +1，返回 101002；计数达到 5 时删除验证码，返回 101003；</li>
     *     <li>验证通过立即删除验证码和失败计数；</li>
     *     <li>手机号不存在就注册：昵称"用户"+手机号后 4 位，头像为空，状态 NORMAL，ID 由 MySQL 自增；
     *     并发注册同一号码时插入会撞 uk_users_phone，这时改为按手机号再查一次；</li>
     *     <li>DISABLED：101004；</li>
     *     <li>生成 UUID token，登录态 hash（userId、nickname、avatar）写 dss:login:token:{token}，TTL 为 dss.auth.token-ttl；</li>
     *     <li>返回 token + 用户资料。</li>
     * </ul>
     */
    LoginVO login(LoginDTO dto);

    /**
     * 登出：删除 dss:login:token:{token}，不影响同一用户的其他 token。
     */
    void logout(String token);
}
