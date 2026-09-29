package com.dss.user.service.impl;

import com.dss.common.exception.NotImplementedException;
import com.dss.common.file.FileUrlResolver;
import com.dss.user.mapper.UserMapper;
import com.dss.user.model.dto.UpdateProfileDTO;
import com.dss.user.model.vo.UserVO;
import com.dss.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 用户资料实现（骨架期是桩）。
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final StringRedisTemplate redisTemplate;
    private final FileUrlResolver fileUrlResolver;

    @Override
    public UserVO getCurrentUser(Long userId) {
        throw new NotImplementedException();
    }

    @Override
    public UserVO updateProfile(Long userId, UpdateProfileDTO dto) {
        throw new NotImplementedException();
    }
}
