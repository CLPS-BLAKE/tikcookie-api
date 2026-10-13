-- KEYS: current, visible, metadata, stock, quota, request, expiry, order->request
-- ARGV: user, product, candidate order, now(ms), queue duration(ms)
local function kind(k) return redis.call('TYPE', k).ok end
local function valid(k, expected) local t=kind(k); return t=='none' or t==expected end
local function integer(v)
  if not v or (v~='0' and not string.match(v,'^[1-9]%d*$')) then return false end
  local n=tonumber(v); return n and n>=0 and n==math.floor(n) and n<=2147483647
end
if not valid(KEYS[6],'hash') then return {'CORRUPT'} end
if redis.call('EXISTS',KEYS[6])==1 then
  local r=redis.call('HMGET',KEYS[6],'user','product','order','accepted','deadline','state')
  if r[1]~=ARGV[1] or r[2]~=ARGV[2] then return {'CONFLICT'} end
  if not r[3] or not tonumber(r[4]) or not tonumber(r[5]) or not r[6] then return {'CORRUPT'} end
  return {'OK',r[3],r[4],r[5],'0'}
end
local expected={'set','zset','hash','string','hash','hash','zset','hash'}
for i=1,8 do if not valid(KEYS[i],expected[i]) then return {'CORRUPT'} end end
-- Only new requests require live cache integrity; retries above retain their binding.
if redis.call('HGET',KEYS[5],'_ready')~='1' then return {'CORRUPT'} end
local m=redis.call('HMGET',KEYS[3],'ready','status','start','end','limit','paused','data','auto')
if m[8]~='0' and m[8]~='1' then return {'CORRUPT'} end
local start=tonumber(m[3]); local ending=tonumber(m[4]); local limit=tonumber(m[5]); local now=tonumber(ARGV[4]); local duration=tonumber(ARGV[5])
local stock=redis.call('GET',KEYS[4]); local quota=redis.call('HGET',KEYS[5],ARGV[1]) or '0'
if not integer(stock) or not integer(quota) or not start or not ending or not limit or limit<1 or limit~=math.floor(limit) or not now or not duration or duration<=0 or not m[7] then return {'CORRUPT'} end
if m[1]~='1' or m[6]~='0' then return {'UNAVAILABLE'} end
if redis.call('SISMEMBER',KEYS[1],ARGV[2])~=1 then return {'OLD_ROUND'} end
if now<start then return {'NOT_STARTED'} end
if now>=ending then return {'ENDED'} end
if m[2]~='ON_SHELF' then return {'OFF_SHELF'} end
if tonumber(stock)<=0 then return {'SOLD_OUT'} end
if tonumber(quota)>=limit then return {'LIMIT'} end
if redis.call('HEXISTS',KEYS[8],ARGV[3])==1 then return {'CORRUPT'} end
local deadline=now+duration
redis.call('DECR',KEYS[4])
redis.call('HINCRBY',KEYS[5],ARGV[1],1)
redis.call('HMSET',KEYS[6],'user',ARGV[1],'product',ARGV[2],'order',ARGV[3],'accepted',ARGV[4],'deadline',tostring(deadline),'state','PENDING','released','0')
redis.call('ZADD',KEYS[7],deadline,KEYS[6])
redis.call('HSET',KEYS[8],ARGV[3],KEYS[6])
if tonumber(stock)==1 then redis.call('ZREM',KEYS[2],ARGV[2]) end
return {'OK',ARGV[3],ARGV[4],tostring(deadline),'1'}
