# 库存扣减聚合方案（已实施）

## 最终方案结构

```
                    奖品库存 (award)                    活动库存 (activity)
                    ──────────────                      ──────────────────
热路径：    Lua 脚本 (decr+锁+MQ outbox)         decr + setNx + 延迟队列 offer
                 ↓                                        ↓
异步回写：   RocketMQ 批量消费 → 幂等同步库存       延迟队列 drainTo → count → UPDATE -count
```

- **奖品库存**：Lua 原子脚本 + Redis outbox → RocketMQ 批量消费
- **活动库存**：原有 decr + setNx + 延迟队列 → drainTo 批量（保持不动）

---

## 一、奖品侧：Lua 脚本 + RocketMQ 批量消费

### 问题

原有实现 decr 和 setNx 是两次独立 Redis 调用，存在并发窗口：

```
库存=1
T1: decr → surplus=0
T2: decr → surplus=-1, 恢复为0, return false
T1: setNx(key_0) → FAIL → 库存已减但锁未加，库存永久丢失
```

### 方案

Lua 脚本将 decr + setNx + outbox SADD 合并为一次原子执行。setNx 失败时自动 INCR 回滚 decr。

```
stock_deduction.lua
──────────────────
1. DECR stockKey           → 扣库存
2. if surplus < 0          → 恢复0, 返回失败
3. SET lockKey NX [PX]     → 滑块锁
4. if 锁失败               → INCR 回滚, 返回失败
5. SADD outbox uniqueEvent → 记录待发送 MQ 事件
6. 返回成功
```

### 改动文件（奖品侧）

| 文件 | 改动 |
|---|---|
| `resources/lua/stock_deduction.lua` | **新增** Lua 脚本 |
| `Constants.java` | 新增 `STRATEGY_AWARD_MQ_OUTBOX_SET` 常量 |
| `IRedisService.java` | 新增 Lua 扣减与 outbox 操作 |
| `RedissonService.java` | 实现 Lua 脚本执行 + outbox + 启动预加载 SHA |
| `StrategyRepository.java` | `subtractionAwardStock()` 改用 Lua 脚本 |
| `RuleStockLogicTreeNode.java` | 移除 `awardStockConsumeSendQueue()` 调用（Lua SADD 替代） |
| `IStrategyRepository.java` | 新增 `takeQueueValueCount()`、`updateStrategyAwardStock(count)` |
| `IRaffleStock.java` | 同上 |
| `DefaultRaffleStrategy.java` | 委托实现 |
| `IStrategyAwardDao.java` | 新增幂等库存快照同步 |
| `strategy_award_mapper.xml` | 新增 `LEAST` 库存同步 SQL |
| `StrategyAwardStockDeductCustomer.java` | RocketMQ 批量消费并聚合库存快照 |
| `UpdateAwardStockJob.java` | MQ outbox 失败补偿投递 |

### MQ 批量消费逻辑

```sql
-- 累计 500 条或等待 1 秒触发聚合；同一奖品取最小 surplus。
UPDATE strategy_award
SET award_count_surplus = LEAST(award_count_surplus, #{surplus})
WHERE strategy_id = #{strategyId} AND award_id = #{awardId};
```

---

## 二、活动侧：延迟队列 + drainTo 批量

活动侧保持原有 decr + setNx + 延迟队列方案，仅将 poll 逐条消费改为 drainTo 批量。

### 改动文件（活动侧）

| 文件 | 改动 |
|---|---|
| `IRaffleActivitySkuDao.java` | 新增 `updateActivitySkuStockBatch(sku, count)` |
| `raffle_activity_sku_mapper.xml` | 新增批量 UPDATE SQL |
| `IActivityRepository.java` | 新增 `takeQueueValues(sku)`、`updateActivitySkuStock(sku, count)` |
| `ActivityRepository.java` | 实现 drainTo + batch update |
| `IRaffleActivitySkuStockService.java` | 新增接口方法 |
| `RaffleActivityAccountQuotaService.java` | 委托实现 |
| `UpdateActivitySkuStockJob.java` | poll → drainTo, UPDATE -1 → UPDATE -count |

### 同时修复

`ActivityRepository.subtractionActivitySkuStock()` 中 `surplus == 0` 返回 false 的 bug 已修复：`surplus >= 0` 才算成功，与奖品逻辑一致。

---

## 三、效果对比

| | 改造前（奖品） | 改造后（奖品 Lua+MQ） | 改造后（活动 drainTo） |
|---|---|---|---|
| decr+锁原子性 | 分离，有并发窗口 | Lua 单线程原子 | 分离（保持不动） |
| 锁失败回滚 | 无 | INCR 回滚 | 无 |
| DB 回写 | poll 逐条 UPDATE -1 | MQ 聚合后幂等同步库存 | drainTo 聚合 UPDATE -count |
| 100 次扣减 DB 写入 | 100 次 | 1 次 | 1 次 |
| Redis 往返 | 3次 (decr+setNx+offer) | 1次 (evalSha) | 2次 (decr+setNx, offer) |
| 崩溃安全 | decr 后崩溃库存丢失 | 全部成功或全部失败 | decr 后崩溃库存丢失 |
