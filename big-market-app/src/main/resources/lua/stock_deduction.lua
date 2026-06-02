-- 库存扣减原子操作: decr + 滑块锁 + 追踪Set
-- KEYS[1] = stockKey     (库存缓存 key)
-- KEYS[2] = trackSetKey  (追踪 Set, 用于批量回写DB)
-- ARGV[1] = expireMs     (锁过期毫秒, "0"=永不过期)
-- ARGV[2] = setMember    (Set 成员, 例: "100001:101")
-- ARGV[3] = lockKeyPrefix(锁 key 前缀, 即 stockKey)
--
-- 返回: {status, surplus}
--   status:  1=成功,  0=锁冲突(已回滚), -1=库存不足
--   surplus: 扣减后的剩余库存

-- 1. 扣减库存
local surplus = redis.call('DECR', KEYS[1])

-- 2. 库存不足: 恢复为0, 返回失败
if surplus < 0 then
    redis.call('SET', KEYS[1], 0)
    return {-1, 0}
end

-- 3. 拼接滑块锁 key: stockKey_surplus
--    例如 strategy_award_count_key_100001_101_99
--    每个库存单元一个唯一锁, 防运营手动恢复库存后超卖
local lockKey = ARGV[3] .. '_' .. surplus

-- 4. 加锁 (NX = 仅当 key 不存在时设置)
local lockOk
if ARGV[1] == '0' then
    -- 无过期时间 (活动未设置结束时间)
    lockOk = redis.call('SET', lockKey, '1', 'NX')
else
    -- 有过期时间 (活动结束时间 + 1天)
    lockOk = redis.call('SET', lockKey, '1', 'PX', ARGV[1], 'NX')
end

-- 5. 锁失败 → 回滚 decr → 返回失败
--    这是并发冲突场景: 两个请求 decr 到同一个 surplus 值
if not lockOk then
    redis.call('INCR', KEYS[1])
    return {0, surplus}
end

-- 6. 加入追踪 Set, 供定时任务 SPOP 后批量 UPDATE DB
redis.call('SADD', KEYS[2], ARGV[2])

-- 7. 返回成功
return {1, surplus}
