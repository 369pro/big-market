# 奖品库存回写：RocketMQ 批量消费方案

## 当前链路

```text
抽奖请求
  -> Lua 原子执行 DECR + SET NX + 写 Redis outbox
  -> 同步发送 RocketMQ，检查 SEND_OK 后删除 outbox
  -> 消费回调进入聚合器，累计 500 条或等待 1 秒触发回写
  -> 同一奖品取最小 surplus
  -> 幂等同步 MySQL 库存
```

Lua 将库存扣减和 outbox 写入放在同一个原子操作中。outbox 成员格式为：

```text
strategyId:awardId:eventId:surplus
```

`eventId` 保证每次成功扣减都是独立事件，避免 Redis Set 去重吞掉重复扣减。

## 批量与幂等

RocketMQ 原生客户端与应用聚合器配置：

- `rocketmq.stock.batch-size = 500`：聚合触发阈值，最后一个消费回调可能使本批略超出阈值。
- `rocketmq.stock.flush-interval-ms = 1000`：从第一条进入空窗口的消息开始计时。
- 消费回调等待聚合器完成数据库更新后才返回 `CONSUME_SUCCESS`。
- 20 个消费线程提供有界并发；聚合回写使用独立单线程，数据库变慢时会形成背压。

消费者按 `strategyId:awardId` 聚合，同一奖品选择最小的 `surplus`，执行：

```sql
UPDATE strategy_award
SET award_count_surplus = LEAST(award_count_surplus, #{surplus})
WHERE strategy_id = #{strategyId} AND award_id = #{awardId};
```

这不是按消息数执行 `stock -= count`，因此 MQ 重投、批次重试和消息乱序不会导致重复扣减。

## 失败补偿

正常数据库回写由应用内聚合定时器或数量阈值触发。`UpdateAwardStockJob` 每 5 秒扫描 Redis outbox，补发尚未收到 SEND_OK 的消息：

- MQ 暂时不可用时，抽奖成功事件不会丢失；
- 发送确认后才删除 outbox；
- 消费异常返回 `RECONSUME_LATER`，RocketMQ 重试；超过默认重试次数后进入对应消费组死信队列，需监控并人工处理；
- 重复发送由库存快照 SQL 保证幂等。

`LEAST` 的前提是同一库存生命周期内只减不增。补库存或重置活动时必须增加库存版本并隔离旧事件，不能直接复用旧消息更新新库存。Redis outbox 的恢复能力依赖 Redis 自身持久化与高可用配置。

部署、Topic 初始化和切换步骤见 [RocketMQ 部署说明](../docs/dev-ops/rocketmq/README.md)。

## 为什么废弃 Set + SPOP 计数

普通 Redis Set 不保存重复成员。若成员只有 `strategyId:awardId`，同一奖品扣减多次最终仍只有一个成员，无法通过 `groupingBy(..., counting())` 得到真实次数。

此外，`SPOP` 会先删除数据；如果应用在数据库提交前宕机，被弹出的记录无法自动恢复。MQ 确认机制和 Redis outbox 分别解决消费可靠性与发送可靠性。
