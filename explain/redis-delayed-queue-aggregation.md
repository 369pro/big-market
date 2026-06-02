# Redis 延迟队列库存扣减 — 聚合消费方案（已实施）

## 当前链路回顾

```
用户抽奖请求
  → Redis DECR 原子扣库存（同步，毫秒级）
  → 消息放入 RDelayedQueue（3s 延迟）
  → 定时任务 drainTo 排空队列
  → DB: UPDATE SET stock = stock - count（一次聚合写入）
```

## 改造前的问题

1. **生产者/消费者 key 不匹配（奖品侧）**：生产者写全局 key `strategy_award_count_query_key`，消费者读 per-award key `strategy_award_count_query_key_{sid}_{aid}`，消息永远消费不到
2. **没有真正的聚合**：每次 `poll()` 只取一条，每条消息一次 UPDATE -1，N 条消息 N 次 DB 写入
3. **消费效率低**：100 条积压消息需要 100 轮定时任务才能全部同步到 DB

## 改造方案：drainTo 捞空 + Batch UPDATE

把 `poll()` 改成 `drainTo()`，一次性排空队列中同个 key 的所有消息，合并成一条 `UPDATE - N`。

```
改造前：队列 5 条 → poll()×5 → UPDATE -1 ×5（5 次 DB）
改造后：队列 5 条 → drainTo() → count=5 → UPDATE -5（1 次 DB）
```

---

## 奖品侧改动

### 1. 修复生产者 key（Bug Fix）

`StrategyRepository.awardStockConsumeSendQueue()`：从全局 key 改为 per-award key，与消费者对齐。

```java
// 改造前：写入全局队列，消费者从 per-award 队列读，永远消费不到
String cacheKey = Constants.RedisKey.STRATEGY_AWARD_COUNT_QUERY_KEY;

// 改造后：写入 per-award 队列，与消费者 key 一致
String cacheKey = Constants.RedisKey.STRATEGY_AWARD_COUNT_QUERY_KEY
        + Constants.UNDERLINE + strategyAwardStockKeyVO.getStrategyId()
        + Constants.UNDERLINE + strategyAwardStockKeyVO.getAwardId();
```

### 2. Repository 新增 drainTo + batch update

`StrategyRepository` 新增两个方法：

```java
@Override
public int takeQueueValueCount(Long strategyId, Integer awardId) {
    String cacheKey = Constants.RedisKey.STRATEGY_AWARD_COUNT_QUERY_KEY
            + Constants.UNDERLINE + strategyId + Constants.UNDERLINE + awardId;
    RBlockingQueue<StrategyAwardStockKeyVO> destinationQueue = redisService.getBlockingQueue(cacheKey);
    List<StrategyAwardStockKeyVO> list = new ArrayList<>();
    destinationQueue.drainTo(list);     // 一次性排空
    return list.size();                 // 返回聚合数量
}

@Override
public void updateStrategyAwardStock(Long strategyId, Integer awardId, int count) {
    strategyAwardDao.updateStrategyAwardStockBatch(strategyId, awardId, count);
}
```

### 3. MyBatis SQL

```xml
<update id="updateStrategyAwardStockBatch">
    update strategy_award
    set award_count_surplus = award_count_surplus - #{count}
    where strategy_id = #{strategyId}
      and award_id = #{awardId}
      and award_count_surplus >= #{count}
</update>
```

`>= #{count}` 确保不会扣成负数。

### 4. 定时任务

`UpdateAwardStockJob.exec()`：

```java
for (StrategyAwardStockKeyVO vo : strategyAwardStockKeyVOS) {
    executor.execute(() -> {
        int count = raffleStock.takeQueueValueCount(vo.getStrategyId(), vo.getAwardId());
        if (count <= 0) return;
        raffleStock.updateStrategyAwardStock(vo.getStrategyId(), vo.getAwardId(), count);
    });
}
```

---

## 活动侧改动

活动侧队列本就是 per-SKU（无 key 不匹配问题），只需把 poll 改 drainTo。

### 1. Repository 新增 drainTo + batch update

`ActivityRepository`：

```java
@Override
public int takeQueueValues(Long sku) {
    String cacheKey = Constants.RedisKey.ACTIVITY_SKU_COUNT_QUERY_KEY + sku;
    RBlockingQueue<ActivitySkuStockKeyVO> destinationQueue = redisService.getBlockingQueue(cacheKey);
    List<ActivitySkuStockKeyVO> list = new ArrayList<>();
    destinationQueue.drainTo(list);
    return list.size();
}

@Override
public void updateActivitySkuStock(Long sku, int count) {
    raffleActivitySkuDao.updateActivitySkuStockBatch(sku, count);
}
```

### 2. MyBatis SQL

```xml
<update id="updateActivitySkuStockBatch">
    update raffle_activity_sku
    set stock_count_surplus = stock_count_surplus - #{count}, update_time = now()
    where sku = #{sku} and stock_count_surplus >= #{count}
</update>
```

### 3. 定时任务

`UpdateActivitySkuStockJob.exec()`：

```java
for (Long sku : skuList) {
    executor.execute(() -> {
        int count = skuStock.takeQueueValues(sku);
        if (count <= 0) return;
        skuStock.updateActivitySkuStock(sku, count);
    });
}
```

---

## 接口层新增方法签名

### 奖品侧

`IRaffleStock` / `IStrategyRepository`：
```java
int takeQueueValueCount(Long strategyId, Integer awardId);
void updateStrategyAwardStock(Long strategyId, Integer awardId, int count);
```

`IStrategyAwardDao`：
```java
void updateStrategyAwardStockBatch(@Param("strategyId") Long strategyId,
                                   @Param("awardId") Integer awardId,
                                   @Param("count") int count);
```

### 活动侧

`IRaffleActivitySkuStockService` / `IActivityRepository`：
```java
int takeQueueValues(Long sku);
void updateActivitySkuStock(Long sku, int count);
```

`IRaffleActivitySkuDao`：
```java
void updateActivitySkuStockBatch(@Param("sku") Long sku, @Param("count") int count);
```

---

## 改动范围一览

### 奖品侧（7 个文件）

| 文件 | 改动 |
|---|---|
| `StrategyRepository.java` | ① 生产者 key 加 per-award 后缀 ② 新增 `takeQueueValueCount` + `updateStrategyAwardStock(count)` |
| `IStrategyRepository.java` | 新增两个方法签名 |
| `IRaffleStock.java` | 新增两个方法签名 |
| `DefaultRaffleStrategy.java` | 委托实现新增的两个方法 |
| `strategy_award_mapper.xml` | 新增 `updateStrategyAwardStockBatch` SQL |
| `IStrategyAwardDao.java` | 新增 `updateStrategyAwardStockBatch` 方法 + `@Param` import |
| `UpdateAwardStockJob.java` | `takeQueueValue` → `takeQueueValueCount`，传 count 给 update |

### 活动侧（7 个文件）

| 文件 | 改动 |
|---|---|
| `ActivityRepository.java` | 新增 `takeQueueValues` + `updateActivitySkuStock(sku, count)` |
| `IActivityRepository.java` | 新增两个方法签名 |
| `IRaffleActivitySkuStockService.java` | 新增两个方法签名 |
| `RaffleActivityAccountQuotaService.java` | 委托实现新增的两个方法 |
| `raffle_activity_sku_mapper.xml` | 新增 `updateActivitySkuStockBatch` SQL |
| `IRaffleActivitySkuDao.java` | 新增 `updateActivitySkuStockBatch` 方法 + `@Param` import |
| `UpdateActivitySkuStockJob.java` | `takeQueueValue` → `takeQueueValues`，传 count 给 update，移除未使用 import |

---

## 效果对比

| | 改造前 | 改造后 |
|---|---|---|
| 队列消费 | `poll()` 每次 1 条 | `drainTo()` 每次捞空 |
| 同 key 100 次扣减 | 100 次 poll + 100 次 UPDATE -1 | 1 次 drainTo + 1 次 UPDATE -100 |
| DB 写入次数 | N（等于队列消息数） | 1（聚合后每 key 1 次） |
| 消费延迟 | N 轮定时任务 | 1 轮定时任务 |
| SQL 防护 | `WHERE surplus > 0` | `WHERE surplus >= #{count}` |
| 奖品 key 一致性 | 生产者全局 / 消费者 per-award（bug） | 统一 per-award |

---

## 备选：Redis Hash 计数器方案

如果想完全去掉队列、靠计数器汇总：

```
Redis DECR 扣库存后 → HINCRBY strategy:award:delta {sid}_{aid} 1
定时任务 → HGETALL 取出所有计数值 → batch UPDATE → HDEL 清空
```

| | drainTo 方案（已实施） | Hash 计数器方案 |
|---|---|---|
| 复杂度 | 低 | 中 |
| 消息丢失风险 | 队列消息持久化 | Hash 字段需手动清理 |
| 回溯能力 | 消息消费后消失 | Hash 值可随时查询 |
| 额外依赖 | 无 | 定时清理逻辑 |
