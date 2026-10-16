SELECT
  CAST(s.id AS CHAR) AS id,
  s.name AS name,
  s.address AS address,
  s.category AS category,
  CASE WHEN JSON_LENGTH(s.images) > 0
       THEN JSON_UNQUOTE(JSON_EXTRACT(s.images, '$[0]')) ELSE NULL END AS cover,
  s.business_hours AS businessHours,
  DATE_FORMAT(s.created_at, '%Y-%m-%d %H:%i:%s') AS createdAt,
  s.updated_at AS sync_updated_at
FROM shops s
WHERE s.updated_at >= DATE_SUB(:sql_last_value, INTERVAL 5 SECOND)
  AND s.updated_at < DATE_SUB(NOW(3), INTERVAL 1 SECOND)
ORDER BY sync_updated_at ASC, s.id ASC
