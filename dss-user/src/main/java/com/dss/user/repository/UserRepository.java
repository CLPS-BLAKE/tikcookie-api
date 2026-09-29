package com.dss.user.repository;

import com.dss.user.model.entity.User;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

/**
 * users 集合。索引 uk_phone（phone 唯一）由 docs/中间件配置.md 4.7 的建结构脚本创建。
 */
public interface UserRepository extends MongoRepository<User, Long> {

    Optional<User> findByPhone(String phone);
}
