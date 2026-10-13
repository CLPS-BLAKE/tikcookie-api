-- No stock decrement. The DB commit has already happened under the product coordinator.
local r=redis.call('TYPE',KEYS[1]).ok; local e=redis.call('TYPE',KEYS[2]).ok
if r~='hash' or (e~='none' and e~='zset') then return 'CORRUPT' end
if redis.call('HGET',KEYS[1],'order')~=ARGV[1] then return 'CONFLICT' end
local state=redis.call('HGET',KEYS[1],'state')
if state~='PENDING' and state~='CREATED' then return state or 'CORRUPT' end
redis.call('HSET',KEYS[1],'state','CREATED')
redis.call('ZREM',KEYS[2],KEYS[1])
return 'CREATED'
