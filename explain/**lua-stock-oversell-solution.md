# Lua 脚本 + Redis Set 解决超卖 & 批量回写 DB

## 1. 当前代码问题分析

### 现有流程

```
RuleStockLogicTreeNode.logic()
  → strategyDispatch.subtractionAwardStock(strategyId, awardId)
    → AbstractStrategyAlgorithm.subtractionAwardStock()
      → IStrategyRepository.subtractionAwardStock(cacheKey, endDateTime)
        → redisService.decr(cacheKey)          // 步骤1: 扣库存
        → redisService.setNx(lockKey)           // 步骤2: 加滑块锁
        → awardStockConsumeSendQueue(vo)        // 步骤3: 入延迟队列
```

### 问题: decr 和 setNx 不是原子的

```java
// StrategyRepository.java:279
long surplus = redisService.decr(cacheKey);        // ← 原子
if (surplus < 0) { ... return false; }
String lockKey = cacheKey + "_" + surplus;
Boolean lock = redisService.setNx(lockKey, ...);  // ← 原子, 但和上一步不在一起
return lock;
```

**两个并发问题:**

**问题A — setNx 失败时 decr 没回滚:**
```
库存=1
T1: decr → surplus=0, 准备 setNx(key_0)
T2: decr → surplus=-1, <0, 恢复为0, return false
T1: setNx(key_0) → FAIL（锁冲突）→ 库存=0, 锁没加, 一个库存单元永久丢失
```

**问题B — 进程崩溃:**
```
库存=1
T1: decr → surplus=0
进程 crash（setNx 前）→ 库存已减但锁未加, 数据不可恢复
```

---

## 2. 方案设计

### 2.1 Lua 脚本: 原子化 decr + setNx + 回滚

```
stock_deduction.lua
──────────────────
KEYS[1] = stockKey    (库存 key,   例: strategy_award_count_key_100001_101)
KEYS[2] = trackSetKey (追踪 Set,   例: strategy_award_track_set)
ARGV[1] = expireMs    (锁过期毫秒, "0"=永不过期)
ARGV[2] = setMember   (Set 成员,   例: "100001:101")
ARGV[3] = lockKeyPrefix (锁 key 前缀, 就是 stockKey, Lua 内拼接 surplus)
```

```lua
-- 1. 扣减库存
local surplus = redis.call('DECR', KEYS[1])

-- 2. 无库存: 恢复为 0, 返回失败
if surplus < 0 then
    redis.call('SET', KEYS[1], 0)
    return {-1, 0}
end

-- 3. 拼接滑块锁 key: stockKey_surplus (每个库存单元一个唯一锁)
local lockKey = ARGV[3] .. '_' .. surplus

-- 4. 加锁
local lockOk
if ARGV[1] == '0' then
    lockOk = redis.call('SET', lockKey, '1', 'NX')
else
    lockOk = redis.call('SET', lockKey, '1', 'PX', ARGV[1], 'NX')
end

-- 5. 锁失败 → 回滚 decr → 返回失败
if not lockOk then
    redis.call('INCR', KEYS[1])
    return {0, surplus}
end

-- 6. 加入追踪 Set, 供定时任务批量回写 DB
redis.call('SADD', KEYS[2], ARGV[2])

return {1, surplus}
```

**返回协议:** Lua table → Java `List<Long>`, 3 种结果:

| 返回值 | 含义 | 处理 |
|---|---|---|
| `[-1, 0]` | 库存不足 | 直接拒绝 |
| `[0, n]` | 锁竞争失败, decr 已回滚 | 重试或拒绝 |
| `[1, n]` | 成功, surplus=n | 继续后续流程 |

**关键:** 整个流程在 Redis 单线程内执行, 天然原子, 不存在并发窗口。`lockKey` 的拼接也放在 Lua 内, 因为 `surplus` 只有 DECR 后才知道。

### 2.2 Redis Set 替代延迟队列做批量回写

**现有方案 (延迟队列):**
```
每次扣减 → offer 进 RDelayedQueue → poll() 逐条消费 → 每条一条 UPDATE
问题: N 条消息 = N 次 UPDATE
```

**新方案 (Set + SPOP 分组计数):**
```
每次扣减 → Lua SADD 进 Set → 定时任务 SPOP + 分组计数 → 一次 UPDATE -count
优势: N 条 = 1 次 UPDATE
```

**Set 存储模型:**
```
strategy_award_track_set = {
    "100001:101",    // strategyId:awardId, 出现 N 次 = N 次扣减
    "100001:101",
    "100001:101",
    "100001:102",
    "100001:102",
}
```

定时任务逻辑:
```
SPOP strategy_award_track_set 1000
  → ["100001:101"×3, "100001:102"×2]
  → 分组计数: {"100001:101": 3, "100001:102": 2}
  → UPDATE strategy_award SET surplus = surplus - 3 WHERE sid=100001 AND aid=101
  → UPDATE strategy_award SET surplus = surplus - 2 WHERE sid=100001 AND aid=102
```

SPOP 是原子的，弹出即消费，不会重复扣减。定时任务中途挂了，剩余数据还在 Set 里等下一轮。

---

## 3. 代码改动清单

### 3.1 新增 Lua 脚本文件

`big-market-app/src/main/resources/lua/stock_deduction.lua`

内容见上方 2.1 节。

### 3.2 IRedisService 新增方法

```java
/**
 * 执行库存扣减 Lua 脚本 (原子: decr + setNx + 回滚 + SADD)
 *
 * @return [status, surplus]  status: 1=成功, 0=锁冲突, -1=库存不足
 */
List<Long> execStockDeduction(String stockKey, String trackSetKey,
                               long expireMs, String setMember, String lockKeyPrefix);

/**
 * SPOP 原子弹出 Set 中指定数量成员, 用于批量消费
 */
Set<String> popFromSet(String key, long count);
```

### 3.3 RedissonService 实现

```java
@Override
public List<Long> execStockDeduction(String stockKey, String trackSetKey,
                                      long expireMs, String setMember, String lockKeyPrefix) {
    RScript script = redissonClient.getScript();
    String sha = loadScript(STOCK_DEDUCTION_LUA);
    return script.evalSha(RScript.Mode.READ_WRITE, sha,
            RScript.ReturnType.MULTI,
            Arrays.asList(stockKey, trackSetKey),
            expireMs, setMember, lockKeyPrefix);
}

@Override
public Set<String> popFromSet(String key, long count) {
    // Redisson RSet 没有 SPOP count, 用 Lua 封装
    RScript script = redissonClient.getScript();
    return script.eval(RScript.Mode.READ_WRITE,
            "return redis.call('SPOP', KEYS[1], ARGV[1])",
            RScript.ReturnType.MULTI,
            Arrays.asList(key), count);
}
```

### 3.4 StrategyRepository 改造

```java
@Override
public Boolean subtractionAwardStock(String cacheKey, Date endDateTime) {
    // 1. 计算锁过期时间
    long expireMs = 0;
    if (endDateTime != null) {
        expireMs = endDateTime.getTime() - System.currentTimeMillis()
                + TimeUnit.DAYS.toMillis(1);
    }

    // 2. 从 cacheKey 提取 strategyId:awardId 作为 Set member
    //    cacheKey 格式: strategy_award_count_key_100001_101
    String setMember = extractStrategyAwardFromKey(cacheKey); // "100001:101"

    // 3. 执行 Lua 脚本 (decr + setNx + 回滚 + SADD 全部原子)
    List<Long> result = redisService.execStockDeduction(
            cacheKey,
            Constants.RedisKey.STRATEGY_AWARD_TRACK_SET,
            expireMs,
            setMember,
            cacheKey);  // lockKey 前缀就是 cacheKey

    int status = result.get(0).intValue();
    if (status != 1) {
        log.warn("库存扣减失败 status={} cacheKey={}", status, cacheKey);
    }
    return status == 1;
}
```

### 3.5 UpdateAwardStockJob 改造

```java
@Scheduled(cron = "0 * * * * ?")
public void exec() {
    RLock lock = redissonClient.getLock("big-market-updateAwardStockJob");
    try {
        boolean isLocked = lock.tryLock(3, 0, TimeUnit.SECONDS);
        if (!isLocked) return;

        // SPOP 一次性弹出 Set 中所有成员 (原子操作)
        Set<String> members = redisService.popFromSet(
                Constants.RedisKey.STRATEGY_AWARD_TRACK_SET, 1000);

        if (members == null || members.isEmpty()) return;

        // 分组计数: "100001:101" ×5 → Map<(sid,aid), count>
        Map<String, Long> countMap = members.stream()
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        for (Map.Entry<String, Long> entry : countMap.entrySet()) {
            String[] parts = entry.getKey().split(":");
            Long strategyId = Long.valueOf(parts[0]);
            Integer awardId = Integer.valueOf(parts[1]);
            int count = entry.getValue().intValue();

            executor.execute(() -> {
                log.info("批量更新奖品库存 strategyId:{} awardId:{} count:{}",
                        strategyId, awardId, count);
                raffleStock.updateStrategyAwardStock(strategyId, awardId, count);
            });
        }
    } catch (Exception e) {
        log.error("批量更新奖品库存失败", e);
    } finally {
        if (lock.isLocked() && lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }
}
```

### 3.6 MyBatis Mapper 新增批量 SQL

```xml
<update id="updateStrategyAwardStockBatch">
    UPDATE strategy_award
    SET award_count_surplus = award_count_surplus - #{count}
    WHERE strategy_id = #{strategyId}
      AND award_id = #{awardId}
      AND award_count_surplus >= #{count}
</update>
```

`>= #{count}` 比逐条 `> 0` 更强 — 确保不会扣成负数。

### 3.7 接口层补充

```java
// IRaffleStock / IStrategyRepository
void updateStrategyAwardStock(Long strategyId, Integer awardId, int count);

// IStrategyAwardDao
void updateStrategyAwardStockBatch(@Param("strategyId") Long strategyId,
                                    @Param("awardId") Integer awardId,
                                    @Param("count") int count);
```

---

## 4. 整体流程图

```
抽奖请求
  │
  ▼
RuleStockLogicTreeNode.logic()
  │
  ▼
subtractionAwardStock(cacheKey, endDateTime)
  │
  ▼
┌─────────────────────────────────────────────────────┐
│ Lua 脚本 (单次 Redis 往返, 原子执行)                  │
│                                                      │
│  1. DECR stockKey           ─── 扣库存               │
│  2. if < 0 → 恢复0, 返回失败                          │
│  3. SET lockKey NX [PX ttl] ─── 滑块锁 (兜底防超卖)  │
│  4. if 失败 → INCR stockKey, 返回失败 (回滚)          │
│  5. SADD trackSetKey member ─── 入追踪 Set            │
│  6. 返回成功                                         │
└─────────────────────────────────────────────────────┘
  │
  ├── 成功 → 继续发奖流程
  └── 失败 → 拒绝/重试


定时任务 (每分钟)
  │
  ▼
┌─────────────────────────────────────────────────────┐
│ UpdateAwardStockJob.exec()                           │
│                                                      │
│  1. SPOP trackSetKey 1000  ─── 原子弹出所有成员       │
│  2. 分组计数: {"100001:101"×5, "100001:102"×2, ...}  │
│  3. 并发批量 UPDATE:                                 │
│     UPDATE strategy_award                            │
│     SET award_count_surplus = surplus - 5            │
│     WHERE strategy_id=100001 AND award_id=101        │
│       AND award_count_surplus >= 5                   │
└─────────────────────────────────────────────────────┘
```

---

## 5. 改动范围一览

| 文件 | 改动 |
|---|---|
| `big-market-app/src/main/resources/lua/stock_deduction.lua` | **新增** Lua 脚本 |
| `Constants.java` | 新增 `STRATEGY_AWARD_TRACK_SET`、`ACTIVITY_SKU_TRACK_SET` 常量 |
| `IRedisService.java` | 新增 `execStockDeduction()`、`popFromSet()` |
| `RedissonService.java` | 实现 Lua 脚本执行、Set SPOP |
| `StrategyRepository.java` | `subtractionAwardStock()` 改用 Lua 脚本 |
| `ActivityRepository.java` | `subtractionActivitySkuStock()` 同样改用 Lua |
| `IStrategyAwardDao.java` | 新增 `updateStrategyAwardStockBatch()` |
| `strategy_award_mapper.xml` | 新增批量 UPDATE SQL |
| `UpdateAwardStockJob.java` | 延迟队列 poll → SPOP Set + 分组批量 UPDATE |
| `UpdateActivitySkuStockJob.java` | 同上 |

**不再使用但保留:** `awardStockConsumeSendQueue()`、`takeQueueValue()` — 延迟队列被 Set 替代。

---

## 6. 与当前方案对比

| | 当前 (decr + setNx 分离 + 延迟队列) | 新方案 (Lua + Set) |
|---|---|---|
| **decr + setNx 原子性** | 否, 两个独立 Redis 命令 | 是, Lua 单线程原子执行 |
| **锁失败回滚** | 无, decr 不恢复, 库存永久丢失 | 有, Lua 内 INCR 回滚 |
| **DB 更新方式** | poll() 逐条 → N 次 UPDATE | SPOP 分组计数 → 1 次 UPDATE |
| **Redis 往返** | 3次/请求 (decr + setNx + offer) | 1次/请求 (Lua eval) |
| **crash 安全** | decr 后 crash → 库存丢失 | Lua 原子: 全部成功或全部失败 |

---

## 7. 实现注意事项

1. **Lua 脚本预加载:** 启动时 `SCRIPT LOAD` 获取 SHA, 后续用 `EVALSHA` 执行, 避免每次传完整脚本。
2. **Redisson 类型转换:** Lua table 返回在 Redisson 中是 `List<Object>`, 元素类型可能是 `Integer` 而非 `Long`, 注意 `((Number) obj).intValue()`。
3. **lockKey 在 Lua 内拼接:** `surplus` 只有 DECR 后才知道, lockKey 的拼接 (`cacheKey_surplus`) 必须在 Lua 里完成, ARGV[3] 传入 cacheKey 作为前缀。
4. **活动 SKU 库存可复用:** `ActivityRepository.subtractionActivitySkuStock()` 有额外逻辑 (库存=0 时发 MQ 清空), Lua 脚本加一个可选分支处理。
5. **Set SPOP 原子性:** `SPOP key count` 是 Redis 3.2+ 原子命令, 弹出即消费, 定时任务中途挂了数据不丢。
