-- Small single-host demo: one ordered result set, no OFFSET paging over changing rows.
-- Bounded overlap replays same-millisecond updates; _id makes this idempotent.
-- Shop updates also refresh name/category on every product in that shop.
SELECT
  CAST(p.id AS CHAR) AS id,
  CAST(p.shop_id AS CHAR) AS shopId,
  p.name AS name,
  CAST(p.contents AS CHAR) AS contents_json,
  s.name AS shopName,
  s.category AS shopCategory,
  p.type AS type,
  p.status AS status,
  p.price AS price,
  p.sold_count AS soldCount,
  p.image AS image,
  DATE_FORMAT(p.flash_start_time, '%Y-%m-%d %H:%i:%s') AS flashStartTime,
  DATE_FORMAT(p.flash_end_time, '%Y-%m-%d %H:%i:%s') AS flashEndTime,
  GREATEST(p.updated_at, s.updated_at) AS sync_updated_at
FROM products p
JOIN shops s ON s.id = p.shop_id
WHERE GREATEST(p.updated_at, s.updated_at) >= DATE_SUB(:sql_last_value, INTERVAL 5 SECOND)
  AND GREATEST(p.updated_at, s.updated_at) < DATE_SUB(NOW(3), INTERVAL 1 SECOND)
ORDER BY sync_updated_at ASC, p.id ASC
