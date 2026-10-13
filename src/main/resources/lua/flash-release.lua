-- KEYS: current, visible, metadata, stock, quota, request, expiry
-- ARGV: user, product, now, TERMINATE|CANCEL (CANCEL only after DB cancellation commit)
local function valid(k, expected) local t=redis.call('TYPE',k).ok; return t=='none' or t==expected end
local expected={'set','zset','hash','string','hash','hash','zset'}
for i=1,7 do if not valid(KEYS[i],expected[i]) then return 'CORRUPT' end end
if redis.call('EXISTS',KEYS[6])==0 then return 'MISSING' end
local r=redis.call('HMGET',KEYS[6],'user','product','state','released')
if r[1]~=ARGV[1] or r[2]~=ARGV[2] then return 'CONFLICT' end
if r[4]=='1' then return 'ALREADY' end
if ARGV[4]~='TERMINATE' and ARGV[4]~='CANCEL' then return 'CORRUPT' end
if r[3]=='CREATED' and ARGV[4]=='TERMINATE' then return 'CREATED' end
if r[3]~='PENDING' and r[3]~='CREATED' then return 'CORRUPT' end
local function integer(v)
  if not v or (v~='0' and not string.match(v,'^[1-9]%d*$')) then return nil end
  local n=tonumber(v); if n and n>=0 and n==math.floor(n) and n<=2147483647 then return n end
end
local stock=integer(redis.call('GET',KEYS[4])); local quota=integer(redis.call('HGET',KEYS[5],ARGV[1])); local now=tonumber(ARGV[3])
local m=redis.call('HMGET',KEYS[3],'ready','status','start','end','paused')
local start=tonumber(m[3]); local ending=tonumber(m[4])
local auto=redis.call('HGET',KEYS[3],'auto')
if redis.call('HGET',KEYS[5],'_ready')~='1' or (auto~='0' and auto~='1') then return 'CORRUPT' end
if not stock or stock<0 or stock~=math.floor(stock) or stock>=2147483647 or not quota or quota<1 or quota~=math.floor(quota) or not now or not start or not ending then return 'CORRUPT' end
redis.call('INCR',KEYS[4]); redis.call('HINCRBY',KEYS[5],ARGV[1],-1)
redis.call('HMSET',KEYS[6],'released','1','state',ARGV[4]=='CANCEL' and 'CANCELLED' or 'TERMINATED')
redis.call('ZREM',KEYS[7],KEYS[6])
if redis.call('SISMEMBER',KEYS[1],ARGV[2])==1 and m[1]=='1' and m[2]=='ON_SHELF' and m[5]=='0' and now>=start and now<ending then
  redis.call('ZADD',KEYS[2],start,ARGV[2])
else redis.call('ZREM',KEYS[2],ARGV[2]) end
return 'RELEASED'
