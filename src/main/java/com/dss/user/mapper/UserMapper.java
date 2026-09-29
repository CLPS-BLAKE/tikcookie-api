package com.dss.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dss.user.model.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * users 表。按手机号查用 selectOne(LambdaQueryWrapper eq phone)，走唯一索引 uk_users_phone。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
