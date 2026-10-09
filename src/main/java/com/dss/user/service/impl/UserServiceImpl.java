package com.dss.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.dss.common.constant.RedisKeys;
import com.dss.common.context.LoginUser;
import com.dss.common.context.UserContext;
import com.dss.common.exception.BizException;
import com.dss.common.file.FileStorageService;
import com.dss.common.file.FileUrlResolver;
import com.dss.user.mapper.UserMapper;
import com.dss.user.model.dto.UpdateProfileDTO;
import com.dss.user.model.entity.User;
import com.dss.user.model.enums.UserErrorCode;
import com.dss.user.model.vo.UserVO;
import com.dss.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;

/**
 * 用户资料实现。
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final StringRedisTemplate redisTemplate;
    private final FileUrlResolver fileUrlResolver;
    /** 上传能力在 common 包（实现在 file 包）：user 模块不 import file 包也能收图存 OSS。 */
    private final FileStorageService fileStorageService;

    @Override
    public UserVO getCurrentUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(UserErrorCode.USER_NOT_FOUND);
        }
        return toUserVO(user);
    }

    @Override
    public UserVO updateProfile(Long userId, UpdateProfileDTO dto) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(UserErrorCode.USER_NOT_FOUND);
        }
        boolean nicknameChanged = dto.getNickname() != null;
        boolean avatarChanged = dto.getAvatar() != null;
        if (!nicknameChanged && !avatarChanged) {
            return toUserVO(user); // 两个字段都没传：不改，直接返回当前资料
        }

        LambdaUpdateWrapper<User> update = new LambdaUpdateWrapper<User>().eq(User::getId, userId);
        if (nicknameChanged) {
            update.set(User::getNickname, dto.getNickname());
        }
        if (avatarChanged) {
            // 空串视为清空头像。置 NULL 只能用 wrapper：updateById(entity) 默认会跳过 null 字段
            update.set(User::getAvatar, dto.getAvatar().isEmpty() ? null : dto.getAvatar());
        }
        // 注意：MetaObjectHandler 只对按实体的更新生效，wrapper 更新要自己写 updated_at（见 MybatisPlusConfig）
        update.set(User::getUpdatedAt, LocalDateTime.now());
        userMapper.update(null, update);

        // 把更新同步回内存对象：后面拼 VO、刷 Redis 登录态都要用新值
        if (nicknameChanged) {
            user.setNickname(dto.getNickname());
        }
        if (avatarChanged) {
            user.setAvatar(dto.getAvatar().isEmpty() ? null : dto.getAvatar());
        }
        syncLoginState(user);
        return toUserVO(user);
    }

    @Override
    public UserVO updateAvatar(Long userId, MultipartFile file) {
        // 先确认用户存在，否则等于给一个无效用户白传一张图进 OSS（token 有效时正常到不了这里）
        if (userMapper.selectById(userId) == null) {
            throw new BizException(UserErrorCode.USER_NOT_FOUND);
        }
        // 收图 → 存 OSS → 拿 fileId；校验或上传失败会抛 106001–106004，此时数据库不动
        String fileId = fileStorageService.uploadImage(file);
        // 复用改资料：写库、同步 Redis 登录态、拼 avatarUrl 都在那里；旧头像对象不删（教学版允许存在）
        UpdateProfileDTO dto = new UpdateProfileDTO();
        dto.setAvatar(fileId);
        return updateProfile(userId, dto);
    }

    /**
     * 只刷新当前请求这个 token 的登录态；同一用户的其它 token 下次登录时自然更新
     * （登录态里的昵称头像只用于展示，不参与鉴权）。
     */
    private void syncLoginState(User user) {
        LoginUser loginUser = UserContext.get();
        if (loginUser == null) {
            return; // 正常到不了：接口只能带着登录态访问
        }
        String tokenKey = RedisKeys.loginToken(loginUser.getToken());
        redisTemplate.opsForHash().put(tokenKey, RedisKeys.TOKEN_FIELD_NICKNAME, user.getNickname());
        if (user.getAvatar() == null) {
            redisTemplate.opsForHash().delete(tokenKey, RedisKeys.TOKEN_FIELD_AVATAR);
        } else {
            redisTemplate.opsForHash().put(tokenKey, RedisKeys.TOKEN_FIELD_AVATAR, user.getAvatar());
        }
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