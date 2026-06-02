# 奖品库存回写：为什么用定时任务 + 替代方案分析

## 当前方案回顾

```
热路径（同步）：
  Lua: decr + setNx + SADD 追踪 Set

异步回写（定时触发）：
  @Scheduled(每分钟) → SPOP Set → 分组计数 → UPDATE -count
```

定时任务在这里的角色是**触发器**——它不持有数据，不参与聚合逻辑。真正的批量聚合发生在 SPOP 弹出那一刻：一次性弹出 1000 条，按 key 分组计数，每个 key 只发一次 UPDATE。

## 为什么不能同步写 MySQL

奖品库存扣减发生在抽奖主链路里：

```
用户请求 → 抽奖算法 → 库存扣减 → 发奖
                         ↑
                    这里如果是 MySQL UPDATE
                    高并发下直接打垮 DB
```

所以必须异步：Redis 承担热点读写，DB 异步追平。定时任务只是"异步"的一种实现方式。

## 替代方案分析

### 方案 A：RocketMQ 延迟消息（逐条消费）

```
热路径：Lua decr 后 → 发 RocketMQ 延迟消息（3s）
消费者：收到消息 → UPDATE -1
```

**问题：没有聚合。** 每条消息触发一次 UPDATE，100 条消息 100 次 DB 写入。要做聚合必须在消费者里自己攒批：

```
消费者内：
  收消息 → buffer.offer(msg)
  另一个线程 @Scheduled → buffer.drainTo → group by → UPDATE -count
```

结果还是需要一个**定时逻辑**来触发批量写入，只是把 Redis SPOP 换成了内存 buffer。且引入新问题：

- **消息丢失**：消费者挂了，内存 buffer 里攒的数据全丢
- **重复消费**：MQ 重试导致同一条扣减被执行多次（需要幂等处理）
- **死信队列**：消费失败的消息需要额外处理

### 方案 B：RocketMQ 延迟消息 + Redis Hash 计数

```
热路径：
  Lua decr 后 → HINCRBY counter {sid}:{aid} 1
  → 发 RocketMQ 延迟消息（仅"flush 信号"，不含数据）

消费者：
  收到 flush → HGETALL counter → UPDATE -count → HDEL counter
```

这个方案**勉强能去掉定时任务**：消息本身就是触发器，Redis Hash 负责攒批。

问题：
- 延迟时间难定：太短攒不够（仍逐条），太长延迟大
- 并发 flush：高并发下多个延迟消息同时到达，HGETALL + HDEL 不是原子的，需要 Lua 包一层
- 依赖增加：引入 RocketMQ，但项目已经在用 RabbitMQ...
- 空 flush：如果没有新扣减，HGETALL 返回空，白触发一次

### 方案 C：RabbitMQ 延迟消息（项目已用 RabbitMQ）

项目已经在用 RabbitMQ（`ActivitySkuStockZeroCustomer` 等），可以用死信队列做延迟：

```
热路径：
  Lua decr → 发消息到队列 A（TTL=3s, DLX=队列 B）
  → 非延迟的直接消息（立即消费）
消费者（队列 B）：
  攒批 2s 内到达的消息 → 分组 → UPDATE -count
```

本质和方案 A 一样——消费者内还是要攒批 + 定时排空。

### 方案 D：保持当前 Set + SPOP（已实施）

```
热路径：Lua SADD（无额外网络开销，已在 Lua 内）
回写：  SPOP → 原子弹出 → 分组计数 → UPDATE -count
触发：  @Scheduled(每分钟)
```

## 方案对比

| | 当前(Set+SPOP) | MQ延迟+内存攒批 | MQ延迟+Hash计数 | MQ延迟(不攒批) |
|---|---|---|---|---|
| **聚合效果** | SPOP 后自然分组 | 需手动攒批+定时排空 | HGETALL 自然分组 | 无聚合 |
| **能否去定时任务** | 不能 | **不能**（消费者内仍需） | **勉强能** | 能（但无聚合） |
| **消息可靠性** | SPOP 原子，弹出即消费 | 消费者挂→攒批全丢 | HGETALL+HDEL 需原子 | MQ 自带可靠性 |
| **额外依赖** | 无 | RocketMQ/RabbitMQ | RocketMQ/RabbitMQ | RocketMQ/RabbitMQ |
| **Redis 往返** | 1次(Lua) | 2次(Lua + 发MQ) | 3次(Lua + HINCRBY + 发MQ) | 2次(Lua + 发MQ) |
| **复杂度** | 低 | 中 | 中高 | 低但不可用 |

## 结论

当前 Set + SPOP 方案已经是这个场景下的最优解：

1. **SPOP 原子**：弹出即消费，定时任务中途挂了数据不丢，Set 里剩余数据等下一轮
2. **零额外依赖**：不引入 MQ，纯 Redis 搞定
3. **天然聚合**：SPOP 后分组计数，同 key 合并为一次 UPDATE
4. **定时任务不是瓶颈**：它只是一个轻量触发器，每分钟触发一次，执行时间毫秒级（SPOP + 分组 + 发几条 UPDATE）

如果要换掉定时任务，唯一可行的替代是方案 B（Hash 计数 + MQ 信号触发），但：
- 项目已用 RabbitMQ 而非 RocketMQ（跨 MQ 迁移成本高）
- 复杂度增加 2 倍，收益仅为去掉一个每分钟跑几十毫秒的定时任务
- `@Scheduled` 是 Spring 内置能力，不引入额外运维负担
