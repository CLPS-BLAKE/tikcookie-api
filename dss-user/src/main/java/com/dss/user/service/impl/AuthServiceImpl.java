package com.dss.user.service.impl;

import com.dss.common.config.DssProperties;
import com.dss.common.exception.NotImplementedException;
import com.dss.common.file.FileUrlResolver;
import com.dss.common.id.RedisIdGenerator;
import com.dss.user.model.dto.LoginDTO;
import com.dss.user.model.vo.LoginVO;
import com.dss.user.repository.UserRepository;
import com.dss.user.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 认证实现（骨架期是桩）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final StringRedisTemplate redisTemplate;
    private final RedisIdGenerator idGenerator;
    private final DssProperties properties;
    private final FileUrlResolver fileUrlResolver;

    @Override
    public void sendSmsCode(String phone) {
        throw new NotImplementedException();
    }

    @Override
    public LoginVO login(LoginDTO dto) {
        throw new NotImplementedException();
    }

    @Override
    public void logout(String token) {
        throw new NotImplementedException();
    }
}
