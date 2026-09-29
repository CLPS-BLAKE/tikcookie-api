-- TikCookie 团购券教学项目：MySQL 8.x 空库初始化
-- 版本：2026-09-29；配套需求文档 v5、接口文档 v3、中间件配置 v5。
-- 用具备建库建表权限的管理账号执行。不要用应用业务账号执行。
-- 只创建结构，不导入账号或商品；不会迁移 MongoDB 或修改已有表结构。
-- 全部业务时间字段约定为 Asia/Shanghai 本地时间，应用端也需使用相同时区。
-- ID 使用 BIGINT AUTO_INCREMENT，Java Long；HTTP JSON 的 ID 输出为字符串。
-- 金额单位为分（整数）。图片字段存 OSS ObjectKey，不存图片字节或完整 URL。
-- 本教学版不建外键：关联对象的存在性由应用校验，重要唯一性由唯一索引约束。

CREATE DATABASE IF NOT EXISTS `dss`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE `dss`;

-- 1. 用户：验证码和 token 放 Redis，不建短信/会话表。
CREATE TABLE IF NOT EXISTS `users` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `phone` CHAR(11) NOT NULL COMMENT '手机号，唯一',
  `nickname` VARCHAR(80) NOT NULL COMMENT '昵称',
  `avatar` VARCHAR(255) NULL COMMENT '头像 OSS ObjectKey',
  `status` VARCHAR(16) NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL/DISABLED',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '注册时间',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
    ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '资料更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_users_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='用户';

-- 2. 店铺：images 是按展示顺序排列的 OSS ObjectKey JSON 数组。
CREATE TABLE IF NOT EXISTS `shops` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '店铺ID',
  `name` VARCHAR(200) NOT NULL COMMENT '店铺名称',
  `address` VARCHAR(500) NOT NULL COMMENT '店铺地址',
  `category` VARCHAR(32) NOT NULL COMMENT 'ShopCategory：FOOD等',
  `images` JSON NOT NULL COMMENT '展示图片 ObjectKey 数组，如 ["group1/M00/00/00/a.jpg"]',
  `business_hours` VARCHAR(100) NULL COMMENT '营业时间',
  `phone` VARCHAR(32) NULL COMMENT '店铺联系电话',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
    ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_shops_category_created` (`category`, `created_at` DESC, `id` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='团购店铺';

-- 3. 商品：NORMAL 的 stock/flash_* /limit_per_user 为 NULL；FLASH 由业务层校验必填。
-- 套餐内容及使用规则使用 JSON，MyBatis-Plus TypeHandler 负责映射。
CREATE TABLE IF NOT EXISTS `products` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '商品ID',
  `shop_id` BIGINT NOT NULL COMMENT '所属店铺ID',
  `name` VARCHAR(240) NOT NULL COMMENT '商品/套餐名称',
  `contents` JSON NOT NULL COMMENT 'ContentGroup 数组；无内容传 []',
  `price` BIGINT NOT NULL COMMENT '当前售价，单位分',
  `type` VARCHAR(16) NOT NULL COMMENT 'NORMAL/FLASH',
  `image` VARCHAR(255) NOT NULL COMMENT '商品主图 OSS ObjectKey',
  `status` VARCHAR(16) NOT NULL DEFAULT 'ON_SHELF' COMMENT 'ON_SHELF/OFF_SHELF',
  `sold_count` INT NOT NULL DEFAULT 0 COMMENT '已售数；支付+1，退款-1',
  `valid_days` INT NOT NULL COMMENT '支付后有效天数',
  `use_rules` JSON NOT NULL COMMENT '使用规则字符串数组；无规则传 []',
  `stock` INT NULL COMMENT 'FLASH 的权威剩余库存；NORMAL 为 NULL',
  `flash_start_time` DATETIME(3) NULL COMMENT 'FLASH 开抢时间',
  `flash_end_time` DATETIME(3) NULL COMMENT 'FLASH 结束时间',
  `limit_per_user` INT NULL COMMENT 'FLASH 每人限购数量',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
    ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_products_shop_status_created` (`shop_id`, `status`, `created_at` DESC, `id` DESC),
  KEY `idx_products_status_created` (`status`, `created_at` DESC, `id` DESC),
  KEY `idx_products_status_sold` (`status`, `sold_count` DESC, `id` DESC),
  KEY `idx_products_type_status_start` (`type`, `status`, `flash_start_time`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='普通/抢购团购商品';

-- 4. 订单：一单一件一券。支付后才生成 voucher_code；未支付可为 NULL。
-- 状态：UNPAID -> UNUSED -> USED，或 UNPAID -> CANCELLED，或 UNUSED -> REFUNDED。
-- 用户点击“去使用”：通过本人 order_id 操作，校验 UNUSED 与 expires_at，再更新 used_at。
-- voucher_code 只用于展示；不能仅凭券码调用使用接口。
CREATE TABLE IF NOT EXISTS `orders` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '订单ID',
  `user_id` BIGINT NOT NULL COMMENT '下单用户ID',
  `product_id` BIGINT NOT NULL COMMENT '购买商品ID',
  `shop_id` BIGINT NOT NULL COMMENT '下单时店铺ID',
  `product_type` VARCHAR(16) NOT NULL COMMENT '下单时 NORMAL/FLASH',
  `snapshot_product_name` VARCHAR(240) NOT NULL COMMENT '下单时商品名',
  `snapshot_product_image` VARCHAR(255) NOT NULL COMMENT '下单时商品图片 OSS ObjectKey',
  `snapshot_shop_name` VARCHAR(200) NOT NULL COMMENT '下单时店铺名',
  `snapshot_price` BIGINT NOT NULL COMMENT '下单时商品价，单位分',
  `amount` BIGINT NOT NULL COMMENT '订单金额，单位分；等于 snapshot_price',
  `status` VARCHAR(16) NOT NULL COMMENT 'UNPAID/UNUSED/USED/CANCELLED/REFUNDED',
  `voucher_code` CHAR(12) NULL COMMENT '支付后生成的全局唯一12位数字券码',
  `pay_channel` VARCHAR(16) NULL COMMENT '支付渠道：MOCK',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '下单时间',
  `pay_deadline` DATETIME(3) NOT NULL COMMENT '支付截止；下单后15分钟',
  `paid_at` DATETIME(3) NULL COMMENT '模拟支付时间',
  `expires_at` DATETIME(3) NULL COMMENT '券过期时间',
  `used_at` DATETIME(3) NULL COMMENT '用户点击去使用的时间',
  `cancelled_at` DATETIME(3) NULL COMMENT '取消时间',
  `cancel_reason` VARCHAR(16) NULL COMMENT 'USER/TIMEOUT',
  `refunded_at` DATETIME(3) NULL COMMENT '模拟退款时间',
  `refund_reason` VARCHAR(16) NULL COMMENT 'USER/EXPIRED',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
    ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '状态更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_orders_voucher_code` (`voucher_code`),
  KEY `idx_orders_user_created` (`user_id`, `created_at` DESC, `id` DESC),
  KEY `idx_orders_user_status_created` (`user_id`, `status`, `created_at` DESC, `id` DESC),
  KEY `idx_orders_product_user_status` (`product_id`, `user_id`, `status`),
  KEY `idx_orders_status_deadline` (`status`, `pay_deadline`, `id`),
  KEY `idx_orders_status_expires` (`status`, `expires_at`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='团购订单与券码';

-- 5. 收藏：同一用户对同类型、同 ID 的目标只能收藏一次。
CREATE TABLE IF NOT EXISTS `favorites` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '收藏记录ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `target_type` VARCHAR(16) NOT NULL COMMENT 'SHOP/PRODUCT',
  `target_id` BIGINT NOT NULL COMMENT '店铺ID或商品ID',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '收藏时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_favorites_user_target` (`user_id`, `target_type`, `target_id`),
  KEY `idx_favorites_user_type_created` (`user_id`, `target_type`, `created_at` DESC, `id` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='店铺与商品收藏';

-- 初始化后可手工执行：SHOW TABLES; SHOW CREATE TABLE orders;
-- 注意：CREATE TABLE IF NOT EXISTS 不会自动修改已经存在的旧表。
