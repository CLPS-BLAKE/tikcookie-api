package com.dss.user.service;

import com.dss.user.model.dto.UpdateProfileDTO;
import com.dss.user.model.vo.UserVO;

/**
 * 用户资料。规则见接口文档 5.1.4–5.1.5、需求文档第 2 节（账号）。
 */
public interface UserService {

    /**
     * 当前用户资料；用户不存在时返回 101005。
     */
    UserVO getCurrentUser(Long userId);

    /**
     * 修改昵称 / 头像：传哪个改哪个，都不传不改；改完同步更新 Redis 登录态里的 nickname、avatar
     * （同一用户可能有多个 token，只更新当前请求的 token 即可，其他 token 下次登录时刷新）。
     * 用户不存在时返回 101005。
     */
    UserVO updateProfile(Long userId, UpdateProfileDTO dto);
}
