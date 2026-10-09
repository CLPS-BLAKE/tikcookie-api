-- ============================================================================
-- TikCookie 抢购（FLASH）演示数据 · 可重复执行（已打包进 classpath）
-- 配套：dss-init.sql（先建 5 张空表）。本脚本只写演示数据，不建表。
--
-- 两种执行方式，二选一：
--   1) 自动播种：后端设 DSS_DEMO_SEED_ENABLED=true，启动时由 DemoDataSeeder 执行
--      （只有库里还没有演示数据时才灌，不会覆盖已有的演示数据/其它数据）。
--   2) 手动执行（可反复跑，跑一次等于重置演示数据）：
--        mysql -uroot -p123456 dss < src/main/resources/db/dss-demo-data.sql
--
-- 设计说明：
--   * 所有时间都用 NOW() 的相对偏移，因此任何时候执行，"即将开始 / 进行中 / 已结束"
--     都能正确演示，不会因为固定日期过期而失效。
--   * 演示数据用固定 ID（用户 1001-1003、店铺 2001-2003、商品 3001-3008、
--     订单 4001-4008），自增列会被顶到这些 ID 之后，之后商家新建的数据不会撞号。
--   * 图片 fileId 是占位 ObjectKey（格式符合 ObjectKeys 校验），OSS 里没有真实图，
--     演示时图片显示为空属正常；要看到图，先用内部上传接口传真实图，再替换 image 字段。
--   * 演示登录账号：手机号 13800000001（小明，用户 1001），验证码看后端日志
--     （dev 下 AuthServiceImpl 会打印“【开发用】手机号 … 的登录验证码：…"）。
-- ============================================================================

USE `dss`;

-- 先清掉本脚本管理范围内的演示数据（只删固定 ID 段，不影响其它数据）
DELETE FROM `orders`   WHERE `id` BETWEEN 4001 AND 4008;
DELETE FROM `products` WHERE `id` BETWEEN 3001 AND 3008;
DELETE FROM `shops`    WHERE `id` BETWEEN 2001 AND 2003;
DELETE FROM `users`    WHERE `id` BETWEEN 1001 AND 1003;

-- ----------------------------------------------------------------------------
-- 1. 用户：1001=演示账号（小明），1002/1003=其它买家（演示限购/多用户抢购）
-- ----------------------------------------------------------------------------
INSERT INTO `users` (`id`, `phone`, `nickname`, `status`) VALUES
  (1001, '13800000001', '小明（演示）', 'NORMAL'),
  (1002, '13800000002', '小红',         'NORMAL'),
  (1003, '13800000003', '阿强',         'NORMAL');

-- ----------------------------------------------------------------------------
-- 2. 店铺：抢购商品都要挂在店铺下，同时也是"商家自建"演示的归属方
-- ----------------------------------------------------------------------------
INSERT INTO `shops`
  (`id`, `name`, `address`, `category`, `images`, `business_hours`, `phone`) VALUES
  (2001, '老张牛肉面馆', '上海市黄浦区南京东路 100 号', 'FOOD',
   '["group1/M00/00/00/20010000000000000000000000000000.jpg"]', '10:00-22:00', '021-12345678'),
  (2002, '甜蜜蜜甜品屋', '上海市静安区愚园路 88 号', 'DESSERT_DRINK',
   '["group1/M00/00/00/20020000000000000000000000000000.jpg"]', '11:00-21:00', '021-23456789'),
  (2003, '快乐星球影城', '上海市浦东新区世纪大道 66 号', 'LEISURE',
   '["group1/M00/00/00/20030000000000000000000000000000.jpg"]', '09:30-23:00', '021-34567890');

-- ----------------------------------------------------------------------------
-- 3. 商品：每个抢购商品对应一种演示场景（见右侧注释）
--    type=FLASH 必须填 stock / flash_start_time / flash_end_time；
--    type=NORMAL 这三列 + limit_per_user 一律 NULL。
-- ----------------------------------------------------------------------------
INSERT INTO `products`
  (`id`, `shop_id`, `name`, `contents`, `price`, `type`, `image`, `status`,
   `sold_count`, `valid_days`, `use_rules`,
   `stock`, `flash_start_time`, `flash_end_time`, `limit_per_user`) VALUES

  -- 场景① 即将开始：专区显示“即将开始”，点击抢购报 103008「抢购还没开始」
  (3001, 2001, '抢购·招牌牛肉面（即将开始）', '[{"title":"主食","items":[{"name":"招牌牛肉面","count":1}]},{"title":"小食","items":[{"name":"卤蛋","count":1}]}]',
   100, 'FLASH', 'group1/M00/00/00/30010000000000000000000000000000.jpg', 'ON_SHELF',
   0, 30, '["周末节假日通用","无需预约"]',
   50, DATE_ADD(NOW(3), INTERVAL 1 HOUR), DATE_ADD(NOW(3), INTERVAL 3 HOUR), 1),

  -- 场景② 进行中·有货：专区显示“进行中”，抢购下单成功 → 生成 UNPAID 订单，库存 -1
  (3002, 2001, '抢购·双人牛肉面套餐（进行中）', '[{"title":"主食","items":[{"name":"牛肉面","count":2}]},{"title":"小食","items":[{"name":"小菜拼盘","count":1}]}]',
   3990, 'FLASH', 'group1/M00/00/00/30020000000000000000000000000000.jpg', 'ON_SHELF',
   356, 30, '["周末节假日通用","无需预约"]',
   100, DATE_SUB(NOW(3), INTERVAL 1 HOUR), DATE_ADD(NOW(3), INTERVAL 2 HOUR), 10),

  -- 场景③ 进行中·售罄：专区显示“已抢光”，点击抢购报 103010「已抢光」
  (3003, 2002, '抢购·杨枝甘露（已抢光）', '[{"title":"甜品","items":[{"name":"杨枝甘露","count":1}]}]',
   888, 'FLASH', 'group1/M00/00/00/30030000000000000000000000000000.jpg', 'ON_SHELF',
   88, 30, '["无需预约"]',
   0, DATE_SUB(NOW(3), INTERVAL 2 HOUR), DATE_ADD(NOW(3), INTERVAL 2 HOUR), 2),

  -- 场景④ 已结束：专区不展示；用详情/历史订单入口可看到，抢购下单报 103009「抢购已结束」
  (3004, 2003, '抢购·双人电影票（已结束）', '[{"title":"票券","items":[{"name":"2D 电影票","count":2}]}]',
   1990, 'FLASH', 'group1/M00/00/00/30040000000000000000000000000000.jpg', 'ON_SHELF',
   200, 30, '["需提前预约"]',
   0, DATE_SUB(NOW(3), INTERVAL 3 HOUR), DATE_SUB(NOW(3), INTERVAL 1 HOUR), 1),

  -- 场景⑤ 每人限购：limit=1 且小明已有一单（4001，未取消），再抢报 103011「超过每人限购数量」
  (3005, 2002, '抢购·提拉米苏（限购1份）', '[{"title":"甜品","items":[{"name":"提拉米苏","count":1}]}]',
   1680, 'FLASH', 'group1/M00/00/00/30050000000000000000000000000000.jpg', 'ON_SHELF',
   30, 30, '["无需预约"]',
   20, DATE_SUB(NOW(3), INTERVAL 1 HOUR), DATE_ADD(NOW(3), INTERVAL 2 HOUR), 1),

  -- 场景⑥ 已下架：专区不展示；抢购下单报 103002「商品已下架」
  (3006, 2001, '抢购·小菜拼盘（已下架）', '[{"title":"小食","items":[{"name":"小菜拼盘","count":1}]}]',
   500, 'FLASH', 'group1/M00/00/00/30060000000000000000000000000000.jpg', 'OFF_SHELF',
   12, 30, '["无需预约"]',
   40, DATE_SUB(NOW(3), INTERVAL 1 HOUR), DATE_ADD(NOW(3), INTERVAL 2 HOUR), 1),

  -- 普通商品：演示普通下单，以及“抢购商品走普通下单(103013) / 普通商品走抢购下单(103012)”的交叉校验
  (3007, 2001, '招牌牛肉面（普通）', '[{"title":"主食","items":[{"name":"招牌牛肉面","count":1}]}]',
   2500, 'NORMAL', 'group1/M00/00/00/30070000000000000000000000000000.jpg', 'ON_SHELF',
   150, 30, '["周末节假日通用","无需预约"]',
   NULL, NULL, NULL, NULL),

  (3008, 2002, '甜品下午茶双人套餐（普通）', '[{"title":"甜品","items":[{"name":"甜品拼盘","count":2}]}]',
   5200, 'NORMAL', 'group1/M00/00/00/30080000000000000000000000000000.jpg', 'ON_SHELF',
   99, 30, '["无需预约"]',
   NULL, NULL, NULL, NULL);

-- ----------------------------------------------------------------------------
-- 4. 订单：覆盖全部 5 种状态；同时驱动“限购”场景（4001 占掉 3005 的限购名额）
--    status 流转：UNPAID→UNUSED→USED；UNPAID→CANCELLED；UNUSED→REFUNDED
-- ----------------------------------------------------------------------------
INSERT INTO `orders`
  (`id`, `user_id`, `product_id`, `shop_id`, `product_type`,
   `snapshot_product_name`, `snapshot_product_image`, `snapshot_shop_name`,
   `snapshot_price`, `amount`, `status`, `voucher_code`, `pay_channel`,
   `created_at`, `pay_deadline`, `paid_at`, `expires_at`, `used_at`,
   `cancelled_at`, `cancel_reason`, `refunded_at`, `refund_reason`) VALUES

  -- 待使用（也是 3005 的限购占用单）：券码可核销/退款
  (4001, 1001, 3005, 2002, 'FLASH',
   '抢购·提拉米苏（限购1份）', 'group1/M00/00/00/30050000000000000000000000000000.jpg', '甜蜜蜜甜品屋',
   1680, 1680, 'UNUSED', '200000000001', 'MOCK',
   DATE_SUB(NOW(3), INTERVAL 1 DAY), DATE_SUB(NOW(3), INTERVAL 1 DAY) + INTERVAL 15 MINUTE,
   DATE_SUB(NOW(3), INTERVAL 1 DAY) + INTERVAL 1 MINUTE, DATE_SUB(NOW(3), INTERVAL 1 DAY) + INTERVAL 30 DAY,
   NULL, NULL, NULL, NULL, NULL),

  -- 待支付：可直接演示“去支付”（pay_deadline 仍在未来）
  (4002, 1001, 3002, 2001, 'FLASH',
   '抢购·双人牛肉面套餐（进行中）', 'group1/M00/00/00/30020000000000000000000000000000.jpg', '老张牛肉面馆',
   3990, 3990, 'UNPAID', NULL, NULL,
   DATE_SUB(NOW(3), INTERVAL 5 MINUTE), DATE_ADD(NOW(3), INTERVAL 10 MINUTE),
   NULL, NULL, NULL, NULL, NULL, NULL, NULL),

  -- 已使用：券码已核销
  (4003, 1001, 3002, 2001, 'FLASH',
   '抢购·双人牛肉面套餐（进行中）', 'group1/M00/00/00/30020000000000000000000000000000.jpg', '老张牛肉面馆',
   3990, 3990, 'USED', '200000000003', 'MOCK',
   DATE_SUB(NOW(3), INTERVAL 3 DAY), DATE_SUB(NOW(3), INTERVAL 3 DAY) + INTERVAL 15 MINUTE,
   DATE_SUB(NOW(3), INTERVAL 3 DAY) + INTERVAL 2 MINUTE, DATE_SUB(NOW(3), INTERVAL 3 DAY) + INTERVAL 30 DAY,
   DATE_SUB(NOW(3), INTERVAL 1 DAY), NULL, NULL, NULL, NULL),

  -- 已取消（用户主动取消）：抢购取消会回补库存、释放限购名额
  (4004, 1001, 3002, 2001, 'FLASH',
   '抢购·双人牛肉面套餐（进行中）', 'group1/M00/00/00/30020000000000000000000000000000.jpg', '老张牛肉面馆',
   3990, 3990, 'CANCELLED', NULL, NULL,
   DATE_SUB(NOW(3), INTERVAL 2 DAY), DATE_SUB(NOW(3), INTERVAL 2 DAY) + INTERVAL 15 MINUTE,
   NULL, NULL, NULL,
   DATE_SUB(NOW(3), INTERVAL 2 DAY) + INTERVAL 5 MINUTE, 'USER', NULL, NULL),

  -- 已退款（用户退款）：退款不回补库存
  (4005, 1001, 3002, 2001, 'FLASH',
   '抢购·双人牛肉面套餐（进行中）', 'group1/M00/00/00/30020000000000000000000000000000.jpg', '老张牛肉面馆',
   3990, 3990, 'REFUNDED', '200000000005', 'MOCK',
   DATE_SUB(NOW(3), INTERVAL 4 DAY), DATE_SUB(NOW(3), INTERVAL 4 DAY) + INTERVAL 15 MINUTE,
   DATE_SUB(NOW(3), INTERVAL 4 DAY) + INTERVAL 1 MINUTE, DATE_SUB(NOW(3), INTERVAL 4 DAY) + INTERVAL 30 DAY,
   NULL, NULL, NULL,
   DATE_SUB(NOW(3), INTERVAL 3 DAY), 'USER'),

  -- 已取消（支付超时，由定时任务取消）
  (4006, 1001, 3004, 2003, 'FLASH',
   '抢购·双人电影票（已结束）', 'group1/M00/00/00/30040000000000000000000000000000.jpg', '快乐星球影城',
   1990, 1990, 'CANCELLED', NULL, NULL,
   DATE_SUB(NOW(3), INTERVAL 1 DAY), DATE_SUB(NOW(3), INTERVAL 1 DAY) + INTERVAL 15 MINUTE,
   NULL, NULL, NULL,
   DATE_SUB(NOW(3), INTERVAL 1 DAY) + INTERVAL 20 MINUTE, 'TIMEOUT', NULL, NULL),

  -- 其它买家的单：让“进行中”商品看起来有真实成交量
  (4007, 1002, 3002, 2001, 'FLASH',
   '抢购·双人牛肉面套餐（进行中）', 'group1/M00/00/00/30020000000000000000000000000000.jpg', '老张牛肉面馆',
   3990, 3990, 'UNUSED', '200000000007', 'MOCK',
   DATE_SUB(NOW(3), INTERVAL 6 HOUR), DATE_SUB(NOW(3), INTERVAL 6 HOUR) + INTERVAL 15 MINUTE,
   DATE_SUB(NOW(3), INTERVAL 6 HOUR) + INTERVAL 2 MINUTE, DATE_SUB(NOW(3), INTERVAL 6 HOUR) + INTERVAL 30 DAY,
   NULL, NULL, NULL, NULL, NULL),

  -- 其它买家的单：售罄商品（3003）的历史成交
  (4008, 1003, 3003, 2002, 'FLASH',
   '抢购·杨枝甘露（已抢光）', 'group1/M00/00/00/30030000000000000000000000000000.jpg', '甜蜜蜜甜品屋',
   888, 888, 'USED', '200000000008', 'MOCK',
   DATE_SUB(NOW(3), INTERVAL 2 DAY), DATE_SUB(NOW(3), INTERVAL 2 DAY) + INTERVAL 15 MINUTE,
   DATE_SUB(NOW(3), INTERVAL 2 DAY) + INTERVAL 1 MINUTE, DATE_SUB(NOW(3), INTERVAL 2 DAY) + INTERVAL 30 DAY,
   DATE_SUB(NOW(3), INTERVAL 1 DAY), NULL, NULL, NULL, NULL);
