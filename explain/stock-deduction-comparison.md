# 库存扣减对比：奖品 vs 活动

## 两套扣减的核心差异

| | 奖品库存 (strategy_award) | 活动库存 (raffle_activity_sku) |
|---|---|---|
| Redis Key | `strategy_award_count_key_{sid}_{aid}` | `activity_sku_stock_count_key_{sku}` |
| 延迟队列 Key | 生产者写全局，消费者读 per-award（不匹配） | 生产者/消费者都按 per-sku |
| 定时任务 | 每分钟，需先查活跃奖品列表再逐个 poll | 每 2 秒，按 SKU 列表直接 poll |
| surplus==0 | 成功（decr 后 >=0 都成功） | 失败（最后一个库存无法消费） |
| 库存归零 | 无特殊处理 | MQ → DB SET 0 + 清队列 |
| 缓存初始化 | 直接覆盖（非幂等） | exists 检查后写入（幂等） |
| 扣减失败 | ALLOW（fallback 其他奖品） | 抛异常（直接失败） |

## surplus==0 Bug

`redisService.decr()` 返回扣减**后**的值。库存 1 → decr → 0，说明当前请求拿走了最后一个。

- 奖品：`surplus < 0` 才失败 → 最后一个正常消耗
- 活动：`surplus == 0` 就返回 false → 最后一个被拒绝

## 延迟队列 key 不匹配 Bug（奖品侧）

生产者 `awardStockConsumeSendQueue` 写入全局 key `strategy_award_count_query_key`，但消费者 `takeQueueValue(sid, aid)` 从 `strategy_award_count_query_key_{sid}_{aid}` 读取，消息永远消费不到。

## 聚合批处理改造

详见 `explain/redis-delayed-queue-aggregation.md` 的 drainTo 方案。核心改动：

- poll() 单条 → drainTo() 捞空 → 按 key 聚合 → UPDATE -count（一次 SQL）
- 活动侧同样改为 drainTo + count 聚合
- 修复奖品侧生产者/消费者 key 不匹配的问题
