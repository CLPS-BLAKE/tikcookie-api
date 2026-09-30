package com.dss.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dss.common.config.DssProperties;
import com.dss.common.constant.RedisKeys;
import com.dss.common.exception.BizException;
import com.dss.common.file.FileUrlResolver;
import com.dss.user.mapper.UserMapper;
import com.dss.user.model.dto.LoginDTO;
import com.dss.user.model.entity.User;
import com.dss.user.model.enums.UserErrorCode;
import com.dss.user.model.enums.UserStatus;
import com.dss.user.model.vo.LoginVO;
import com.dss.user.model.vo.UserVO;
import com.dss.user.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 认证实现。验证码和登录态都在 Redis，key 定义见 {@link RedisKeys}（对应 docs/中间件配置.md 第 4 节）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserMapper userMapper;
    private final StringRedisTemplate redisTemplate;
    private final DssProperties properties;
    private final FileUrlResolver fileUrlResolver;

    @Override
    public void sendSmsCode(String phone) {
        // setIfAbsent 是原子操作：60 秒内只有第一个请求能写上间隔标记，后面的直接拒绝
        Boolean first = redisTemplate.opsForValue()
                .setIfAbsent(RedisKeys.loginCodeInterval(phone), "1", RedisKeys.LOGIN_CODE_INTERVAL);
        if (!Boolean.TRUE.equals(first)) {
            throw new BizException(UserErrorCode.SMS_CODE_TOO_FREQUENT);
        }

        // 6 位数字验证码，%06d 补足前导零（如 "003812"）；重发覆盖旧码
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        redisTemplate.opsForValue().set(RedisKeys.loginCode(phone), code, RedisKeys.LOGIN_CODE_TTL);
        // 拿到新验证码后，之前的失败计数作废
        redisTemplate.delete(RedisKeys.loginCodeFail(phone));

        // 不接短信网关：验证码打印日志，开发/演示时去后端日志里看（需求文档第 2 节）
        log.info("【开发用】手机号 {} 的登录验证码：{}（{} 分钟内有效）",
                phone, code, RedisKeys.LOGIN_CODE_TTL.toMinutes());
    }

    @Override
    public LoginVO login(LoginDTO dto) {
        String phone = dto.getPhone();
        String codeKey = RedisKeys.loginCode(phone);
        String savedCode = redisTemplate.opsForValue().get(codeKey);

        if (savedCode == null || !savedCode.equals(dto.getCode())) {
            // 验证码不存在、过期或对不上：都算一次失败，累计错误次数
            long fails = incrementCodeFail(phone);
            if (fails >= RedisKeys.LOGIN_CODE_MAX_FAIL) {
                // 第 5 次失败：作废验证码，必须重新获取（101003）
                redisTemplate.delete(codeKey);
                throw new BizException(UserErrorCode.SMS_CODE_LOCKED);
            }
            throw new BizException(UserErrorCode.SMS_CODE_INVALID);
        }

        // 验证码一次性使用：验证通过就删掉，同时清空失败计数
        redisTemplate.delete(codeKey);
        redisTemplate.delete(RedisKeys.loginCodeFail(phone));

        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getPhone, phone));
        if (user == null) {
            user = register(phone); // 新手机号自动注册
        }
        if (user.getStatus() == UserStatus.DISABLED) {
            throw new BizException(UserErrorCode.USER_DISABLED);
        }

        // UUID token；登录态只存展示用的最小信息
        String token = UUID.randomUUID().toString();
        saveLoginState(token, user);

        LoginVO vo = new LoginVO();
        vo.setToken(token);
        vo.setUser(toUserVO(user));
        return vo;
    }

    @Override
    public void logout(String token) {
        // 只删这一个 token；同一用户的其它登录不受影响
        redisTemplate.delete(RedisKeys.loginToken(token));
    }

    /** 自动注册；并发注册同一手机号会撞唯一索引 uk_users_phone，这时改为再查一次。 */
    private User register(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickname("用户" + phone.substring(phone.length() - 4));
        user.setStatus(UserStatus.NORMAL);
        try {
            userMapper.insert(user); // id 由 MySQL 自增，createdAt / updatedAt 自动填充
            return user;
        } catch (DuplicateKeyException e) {
            // 另一个请求已经建好了同一个手机号，直接用它
            User existing = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getPhone, phone));
            if (existing == null) {
                throw e; // 正常走不到这里（插入失败说明记录必然存在），只是保险
            }
            return existing;
        }
    }

    /** 失败计数 +1；首次计数时设置 5 分钟 TTL（和验证码一致）。 */
    private long incrementCodeFail(String phone) {
        String failKey = RedisKeys.loginCodeFail(phone);
        Long fails = redisTemplate.opsForValue().increment(failKey);
        if (fails != null && fails == 1L) {
            redisTemplate.expire(failKey, RedisKeys.LOGIN_CODE_TTL);
        }
        return fails == null ? 1L : fails;
    }

    /** 登录态 hash（userId、nickname、avatar）；avatar 为空时不写这个字段。 */
    private void saveLoginState(String token, User user) {
        Map<String, String> session = new HashMap<>();
        session.put(RedisKeys.TOKEN_FIELD_USER_ID, String.valueOf(user.getId()));
        session.put(RedisKeys.TOKEN_FIELD_NICKNAME, user.getNickname());
        if (user.getAvatar() != null) {
            session.put(RedisKeys.TOKEN_FIELD_AVATAR, user.getAvatar());
        }
        String tokenKey = RedisKeys.loginToken(token);
        redisTemplate.opsForHash().putAll(tokenKey, session);
        redisTemplate.expire(tokenKey, properties.getAuth().getTokenTtl());
    }

    private UserVO toUserVO(User user) {
        UserVO vo = new UserVO();
        vo.setId(String.valueOf(user.getId()));
        vo.setPhone(user.getPhone());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setAvatarUrl(fileUrlResolver.toUrl(user.getAvatar()));
        vo.setCreateTime(user.getCreatedAt());
        return vo;
    }
}