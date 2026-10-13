-- Appended inside the initializer transaction; never invoke the legacy reset script.
-- Managed shops only. The initializer validates existing shop ownership first.
-- Parameters: 3 products; stock 10/20/30; limit 1/2/2; start +60 seconds; duration 30 minutes.
SET @demo_now = NOW(3);
SET @demo_start = DATE_ADD(@demo_now, INTERVAL 60 SECOND);
SET @demo_end = DATE_ADD(@demo_start, INTERVAL 30 MINUTE);
INSERT IGNORE INTO shops
(id, name, address, category, images, business_hours, phone) VALUES
(2001, '演示·老张牛肉面馆', '上海市黄浦区南京东路100号', 'FOOD', '[]', '10:00-22:00', '021-12345678'),
(2002, '演示·甜蜜蜜甜品屋', '上海市静安区愚园路88号', 'DESSERT_DRINK', '[]', '11:00-21:00', '021-23456789'),
(2003, '演示·快乐星球影城', '上海市浦东新区世纪大道66号', 'LEISURE', '[]', '09:30-23:00', '021-34567890');
UPDATE products SET status = 'OFF_SHELF', updated_at = @demo_now
WHERE type = 'FLASH' AND shop_id IN (2001, 2002, 2003);
INSERT INTO products
(shop_id, name, contents, price, type, image, status, sold_count, valid_days, use_rules, stock, flash_start_time, flash_end_time, limit_per_user, created_at, updated_at)
VALUES (2001, '演示抢购·招牌牛肉面', '[{"title":"主食","items":[{"name":"牛肉面","count":1}]}]', 100, 'FLASH', 'group1/M00/00/00/31bf468adafd43ecad929b5d5e6b6451.jpg', 'OFF_SHELF', 0, 30, '["无需预约","教学模拟支付"]', 10, @demo_start, @demo_end, 1, @demo_now, @demo_now);
SET @demo_product_1 = LAST_INSERT_ID();
INSERT INTO products
(shop_id, name, contents, price, type, image, status, sold_count, valid_days, use_rules, stock, flash_start_time, flash_end_time, limit_per_user, created_at, updated_at)
VALUES (2002, '演示抢购·提拉米苏', '[{"title":"甜品","items":[{"name":"提拉米苏","count":1}]}]', 1680, 'FLASH', 'group1/M00/00/00/56b48505c9534fc192202bd3746b34b4.jpg', 'OFF_SHELF', 0, 30, '["教学模拟支付"]', 20, @demo_start, @demo_end, 2, @demo_now, @demo_now);
SET @demo_product_2 = LAST_INSERT_ID();
INSERT INTO products
(shop_id, name, contents, price, type, image, status, sold_count, valid_days, use_rules, stock, flash_start_time, flash_end_time, limit_per_user, created_at, updated_at)
VALUES (2003, '演示抢购·双人电影票', '[{"title":"票券","items":[{"name":"电影票","count":2}]}]', 1990, 'FLASH', 'group1/M00/00/00/6f8fc80e1d884219a8c677d6f077c1cf.jpg', 'OFF_SHELF', 0, 30, '["教学模拟支付"]', 30, @demo_start, @demo_end, 2, @demo_now, @demo_now);
SET @demo_product_3 = LAST_INSERT_ID();
