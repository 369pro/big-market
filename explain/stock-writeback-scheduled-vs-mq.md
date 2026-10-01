# 奖品库存回写：RabbitMQ 批量消费方案

## 当前链路

```text
抽奖请求
  -> Lua 原子执行 DECR + SET NX + 写 Redis outbox
  -> 尝试发送 RabbitMQ，broker confirm 后删除 outbox
  -> 消费者最多收取 500 条，或等待 1 秒形成一批
  -> 同一奖品取最小 surplus
  -> 幂等同步 MySQL 库存
```

Lua 将库存扣减和 outbox 写入放在同一个原子操作中。outbox 成员格式为：

```text
strategyId:awardId:eventId:surplus
```

`eventId` 保证每次成功扣减都是独立事件，避免 Redis Set 去重吞掉重复扣减。

## 批量与幂等

RabbitMQ 专用监听容器配置：

- `batchSize = 500`
- `prefetchCount = 500`
- `receiveTimeout = 1000ms`

消费者按 `strategyId:awardId` 聚合，同一奖品选择最小的 `surplus`，执行：

```sql
UPDATE strategy_award
SET award_count_surplus = LEAST(award_count_surplus, #{surplus})
WHERE strategy_id = #{strategyId} AND award_id = #{awardId};
```

这不是按消息数执行 `stock -= count`，因此 MQ 重投、批次重试和消息乱序不会导致重复扣减。

## 失败补偿

正常数据库回写不再依赖定时任务。`UpdateAwardStockJob` 只扫描 Redis outbox，补发尚未收到 broker confirm 的消息：

- MQ 暂时不可用时，抽奖成功事件不会丢失；
- 发送确认后才删除 outbox；
- 消费异常会抛出，RabbitMQ 不会确认该批消息；
- 重复发送由库存快照 SQL 保证幂等。

## 为什么废弃 Set + SPOP 计数

普通 Redis Set 不保存重复成员。若成员只有 `strategyId:awardId`，同一奖品扣减多次最终仍只有一个成员，无法通过 `groupingBy(..., counting())` 得到真实次数。

此外，`SPOP` 会先删除数据；如果应用在数据库提交前宕机，被弹出的记录无法自动恢复。MQ 确认机制和 Redis outbox 分别解决消费可靠性与发送可靠性。
