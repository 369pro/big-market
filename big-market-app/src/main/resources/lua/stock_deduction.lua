-- 库存扣减原子操作: decr + 滑块锁 + MQ outbox
-- KEYS[1] = stockKey     (库存缓存 key)
-- KEYS[2] = outboxSetKey (MQ outbox Set, 用于发送失败补偿)
-- ARGV[1] = expireMs     (锁过期毫秒, "0"=永不过期)
-- ARGV[2] = memberPrefix (成员前缀, 例: "100001:101:uuid")
-- ARGV[3] = lockKeyPrefix(锁 key 前缀, 即 stockKey)
--
-- 返回: {status, surplus}
--   status:  1=成功,  0=锁冲突(已回滚), -1=库存不足
--   surplus: 扣减后的剩余库存

-- 1. 预验证 TTL 和 key 类型等已知错误，避免这些错误导致部分写入
local expireMs = ARGV[1]
local expireMsNumber = expireMs and tonumber(expireMs)
if not expireMsNumber
        or (expireMs ~= '0' and not string.match(expireMs, '^[1-9]%d*$')) then
    return redis.error_reply('expireMs must be a non-negative integer')
end
if #expireMs > 19 or (#expireMs == 19 and expireMs > '9223372036854775807') then
    return redis.error_reply('expireMs is out of range')
end
if #expireMs == 19 then
    local serverTime = redis.call('TIME')
    local nowMs = tonumber(serverTime[1]) * 1000
            + math.floor(tonumber(serverTime[2]) / 1000)
    -- Leave a small margin for Redis to add the TTL to its current time.
    if expireMsNumber > 9223372036854775000 - nowMs - 1000000 then
        return redis.error_reply('expireMs is out of range')
    end
end
if not ARGV[2] or ARGV[2] == '' then
    return redis.error_reply('memberPrefix is required')
end
if not ARGV[3] or ARGV[3] == '' then
    return redis.error_reply('lockKeyPrefix is required')
end

if redis.call('EXISTS', KEYS[1]) == 0 then
    -- 缺失库存视为库存不足，但不能让 DECR 隐式创建空库存 key。
    return {-1, 0}
end

local stockType = redis.call('TYPE', KEYS[1]).ok
if stockType ~= 'string' then
    return redis.error_reply('stock key must be a string')
end

local outboxType = redis.call('TYPE', KEYS[2]).ok
if outboxType ~= 'none' and outboxType ~= 'set' then
    return redis.error_reply('outbox key must be a set')
end

-- 2. 扣减库存
local surplus = redis.call('DECR', KEYS[1])

-- 3. 库存不足: 恢复为0, 返回失败
if surplus < 0 then
    redis.call('SET', KEYS[1], 0)
    return {-1, 0}
end
-- 4. 拼接【滑块锁】 key: stockKey_surplus
--    例如 strategy_award_count_key_100001_101_99
--    每个库存单元一个唯一锁, 防运营手动恢复库存后超卖
local lockKey = ARGV[3] .. '_' .. surplus

-- 5. 加锁 (NX = 仅当 key 不存在时设置)
local lockOk
if expireMsNumber == 0 then
    -- 无过期时间 (活动未设置结束时间)
    lockOk = redis.call('SET', lockKey, '1', 'NX')
else
    -- 有过期时间 (活动结束时间 + 1天)
    lockOk = redis.call('SET', lockKey, '1', 'PX', ARGV[1], 'NX')
end

-- 6. 锁失败 → 回滚 decr → 返回失败
--    这是库存被人工恢复或对应滑块锁仍存在导致的冲突
if not lockOk then
    redis.call('INCR', KEYS[1])
    return {0, surplus}
end
-- 7. 写入 MQ outbox。surplus 对同一奖品的每次成功扣减唯一，Set 不会吞掉重复扣减。
redis.call('SADD', KEYS[2], ARGV[2] .. ':' .. surplus)

-- 8. 返回成功
return {1, surplus}
